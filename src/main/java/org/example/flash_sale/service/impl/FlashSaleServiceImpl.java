package org.example.flash_sale.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.dto.FlashSaleRequest;
import org.example.flash_sale.dto.FlashSaleResponse;
import org.example.flash_sale.dto.OrderMessage;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.mq.OrderProducer;
import org.example.flash_sale.service.FlashSaleService;
import org.example.flash_sale.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

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
        if (result == null || result < -1) {
            return FlashSaleResponse.fail(Constants.CODE_SYSTEM_BUSY, "FlashSaleServiceImpl, 系统繁忙，请稍后再试");
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
     * 生成订单号
     * 格式：FS + 时间戳 + 用户ID后4位 + 商品ID后4位 + 随机数
     */
    private String generateOrderNo(Long userId, Long productId) {
        return "FS" + IdUtil.getSnowflakeNextIdStr();
    }
}
