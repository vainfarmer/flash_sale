package org.example.flash_sale.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.dto.FlashSaleRequest;
import org.example.flash_sale.dto.FlashSaleResponse;
import org.example.flash_sale.dto.OrderMessage;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.mapper.OrderMapper;
import org.example.flash_sale.mapper.ProductMapper;
import org.example.flash_sale.mq.OrderProducer;
import org.example.flash_sale.service.FlashSaleService;
import org.example.flash_sale.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 秒杀服务实现
 * 核心逻辑：Redis Lua脚本扣减库存 + Kafka异步下单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final ProductService productService;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderProducer orderProducer;

    @Override
    public FlashSaleResponse doFlashSale(Long userId, FlashSaleRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();

        log.info("用户 {} 发起秒杀请求: productId={}, quantity={}", userId, productId, quantity);

        // 1. 检查活动状态
        if (!checkActivityStatus(productId)) {
            return FlashSaleResponse.fail(Constants.CODE_ACTIVITY_NOT_START, "活动未开始或已结束");
        }

        // 2. 获取商品信息
        Product product = productService.getById(productId);
        if (product == null) {
            return FlashSaleResponse.fail(400, "商品不存在");
        }

        // 3. 检查并确保库存已预热到Redis
        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        String boughtKey = Constants.REDIS_USER_BOUGHT_KEY + productId;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            log.info("库存未预热，自动预热商品: {}", productId);
            productService.warmUpProductCache(productId);
        }

        // 4. 执行Lua脚本扣减库存（原子操作）
        Long result = redisTemplate.execute(
                stockDeductScript,
                Arrays.asList(stockKey, boughtKey),
                userId.toString(),
                quantity.toString(),
                product.getLimitPerUser().toString());

        log.info("Lua脚本执行结果: result={}", result);

        // 5. 处理扣减结果
        if (result == null) {
            return FlashSaleResponse.fail(Constants.CODE_SYSTEM_BUSY, "系统繁忙，请稍后再试");
        }

        if (result == -1) {
            return FlashSaleResponse.fail(Constants.CODE_STOCK_NOT_ENOUGH, "库存不足，秒杀失败");
        }

        if (result == -2) {
            return FlashSaleResponse.fail(Constants.CODE_REPEAT_BUY, "您已参与过此活动或超出限购数量");
        }

        // 6. 库存扣减成功，生成订单号
        String orderNo = generateOrderNo(userId, productId);

        // 7. 发送订单消息到Kafka（异步创建订单）
        OrderMessage orderMessage = OrderMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .productId(productId)
                .productName(product.getProductName())
                .quantity(quantity)
                .amount(product.getFlashPrice().multiply(new java.math.BigDecimal(quantity)))
                .timestamp(System.currentTimeMillis())
                .build();

        orderProducer.sendOrderMessage(orderMessage);

        log.info("秒杀成功，订单消息已发送: orderNo={}", orderNo);

        // 8. 返回成功响应
        return FlashSaleResponse.success(orderNo);
    }

    /**
     * 异步秒杀（使用虚拟线程执行器）
     * 释放Tomcat线程，提高并发处理能力
     */
    @Override
    @Async("flashSaleExecutor")
    public CompletableFuture<FlashSaleResponse> doFlashSaleAsync(Long userId, FlashSaleRequest request) {
        log.debug("异步秒杀开始: userId={}, thread={}", userId, Thread.currentThread());
        FlashSaleResponse response = doFlashSale(userId, request);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * 异步直接数据库秒杀
     * 注意：使用普通线程池而非虚拟线程！
     * 原因：数据库操作涉及 HikariCP/JDBC 的 synchronized 块，
     * 虚拟线程会被"钉住"(pinning)，导致载体线程饥饿
     */
    @Override
    @Async("taskExecutor") // 使用普通线程池，避免虚拟线程 pinning
    public CompletableFuture<FlashSaleResponse> doFlashSaleDirectAsync(Long userId, FlashSaleRequest request) {
        log.debug("异步直接秒杀开始: userId={}, thread={}", userId, Thread.currentThread());
        FlashSaleResponse response = doFlashSaleDirect(userId, request);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public boolean checkActivityStatus(Long productId) {
        Product product = productService.getById(productId);
        if (product == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查活动时间
        if (now.isBefore(product.getStartTime())) {
            log.info("活动未开始: productId={}", productId);
            return false;
        }

        if (now.isAfter(product.getEndTime())) {
            log.info("活动已结束: productId={}", productId);
            return false;
        }

        // 检查活动状态
        if (product.getStatus() != Constants.ACTIVITY_STATUS_RUNNING) {
            log.info("活动状态异常: productId={}, status={}", productId, product.getStatus());
            return false;
        }

        return true;
    }

    /**
     * 乐观锁最大重试次数
     */
    private static final int MAX_RETRY_TIMES = 10;

    /**
     * 直接操作数据库的秒杀方法（用于性能对比）
     * 不使用Redis缓存、Lua脚本、Kafka
     * 直接查询数据库、扣减库存、创建订单
     * 使用乐观锁 + 重试机制
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashSaleResponse doFlashSaleDirect(Long userId, FlashSaleRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();

        log.debug("用户 {} 发起直接秒杀请求: productId={}, quantity={}", userId, productId, quantity);

        // 1. 检查是否重复购买（先检查，避免无效重试）
        List<Order> existOrders = orderMapper.selectByUserAndProduct(userId, productId);
        int boughtCount = existOrders.stream()
                .filter(o -> o.getStatus() != Constants.ORDER_STATUS_CANCELLED)
                .mapToInt(Order::getQuantity)
                .sum();

        // 2. 乐观锁重试机制
        for (int retry = 0; retry < MAX_RETRY_TIMES; retry++) {
            // 每次重试都重新读取最新数据
            Product product = productMapper.selectById(productId);
            if (product == null) {
                return FlashSaleResponse.fail(400, "商品不存在");
            }

            // 检查活动状态
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(product.getStartTime()) || now.isAfter(product.getEndTime())) {
                return FlashSaleResponse.fail(Constants.CODE_ACTIVITY_NOT_START, "活动未开始或已结束");
            }
            if (product.getStatus() != Constants.ACTIVITY_STATUS_RUNNING) {
                return FlashSaleResponse.fail(Constants.CODE_ACTIVITY_NOT_START, "活动未开始");
            }

            // 检查库存
            if (product.getAvailableStock() < quantity) {
                return FlashSaleResponse.fail(Constants.CODE_STOCK_NOT_ENOUGH, "库存不足，秒杀失败");
            }

            // 检查限购
            if (boughtCount + quantity > product.getLimitPerUser()) {
                return FlashSaleResponse.fail(Constants.CODE_REPEAT_BUY, "超出限购数量");
            }

            // 使用乐观锁扣减库存
            int rows = productMapper.deductStockWithOptimisticLock(
                    productId, quantity, product.getVersion());

            if (rows > 0) {
                // 扣减成功，创建订单
                String orderNo = generateOrderNo(userId, productId);
                Order order = new Order();
                order.setOrderNo(orderNo);
                order.setUserId(userId);
                order.setProductId(productId);
                order.setProductName(product.getProductName());
                order.setQuantity(quantity);
                order.setAmount(product.getFlashPrice().multiply(BigDecimal.valueOf(quantity)));
                order.setStatus(Constants.ORDER_STATUS_PENDING);

                orderMapper.insert(order);
                log.debug("直接秒杀成功(重试{}次): orderNo={}", retry, orderNo);
                return FlashSaleResponse.success(orderNo);
            }

            // 乐观锁冲突，重试
            log.debug("乐观锁冲突，第{}次重试", retry + 1);
        }

        // 重试次数用完仍然失败
        return FlashSaleResponse.fail(Constants.CODE_STOCK_NOT_ENOUGH, "系统繁忙，请重试");
    }

    /**
     * 生成订单号
     * 格式：FS + 雪花ID
     */
    private String generateOrderNo(Long userId, Long productId) {
        return "FS" + IdUtil.getSnowflakeNextIdStr();
    }
}
