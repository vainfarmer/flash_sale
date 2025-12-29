package org.example.flash_sale.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.mapper.OrderMapper;
import org.example.flash_sale.mapper.ProductMapper;
import org.example.flash_sale.service.StockConsistencyService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 库存一致性检验服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockConsistencyServiceImpl implements StockConsistencyService {

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public ConsistencyCheckResult checkConsistency(Long productId) {
        log.info("开始检查商品 {} 的库存一致性", productId);

        // 1. 获取数据库中的商品信息
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return new ConsistencyCheckResult(
                    productId, null, null, null, null, null,
                    false, "商品不存在"
            );
        }

        // 2. 获取数据库中的库存
        int dbStock = product.getAvailableStock();

        // 3. 获取Redis中的库存
        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        Object redisStockObj = redisTemplate.opsForValue().get(stockKey);
        Integer redisStock = null;
        if (redisStockObj != null) {
            redisStock = parseInteger(redisStockObj);
        }

        // 4. 计算数据库中已售数量（通过订单表）
        int dbSoldCount = calculateDbSoldCount(productId);

        // 5. 计算Redis中已售数量（通过购买记录Hash）
        int redisSoldCount = calculateRedisSoldCount(productId);

        // 6. 检查一致性
        boolean stockConsistent = redisStock != null && dbStock == redisStock;
        boolean soldConsistent = dbSoldCount == redisSoldCount;
        boolean isConsistent = stockConsistent && soldConsistent;

        String message;
        if (redisStock == null) {
            message = "Redis库存不存在（未预热）";
            isConsistent = false;
        } else if (!stockConsistent) {
            message = String.format("库存不一致: DB=%d, Redis=%d, 差异=%d",
                    dbStock, redisStock, dbStock - redisStock);
        } else if (!soldConsistent) {
            message = String.format("已售数量不一致: DB订单=%d, Redis记录=%d",
                    dbSoldCount, redisSoldCount);
        } else {
            message = "一致";
        }

        ConsistencyCheckResult result = new ConsistencyCheckResult(
                productId,
                product.getProductName(),
                dbStock,
                redisStock,
                dbSoldCount,
                redisSoldCount,
                isConsistent,
                message
        );

        log.info("商品 {} 一致性检查结果: {}", productId, result);
        return result;
    }

    @Override
    public void checkAllProductsConsistency() {
        log.info("========== 开始全量库存一致性检查 ==========");

        List<Product> products = productMapper.selectList(null);
        int totalCount = products.size();
        int consistentCount = 0;
        int inconsistentCount = 0;

        for (Product product : products) {
            ConsistencyCheckResult result = checkConsistency(product.getId());
            if (result.isConsistent()) {
                consistentCount++;
            } else {
                inconsistentCount++;
                log.warn("发现不一致: 商品={}, 问题={}", product.getProductName(), result.message());
            }
        }

        log.info("========== 一致性检查完成 ==========");
        log.info("总计: {} 个商品, 一致: {}, 不一致: {}", totalCount, consistentCount, inconsistentCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repairConsistency(Long productId, boolean useDbAsSource) {
        log.info("开始修复商品 {} 的库存一致性, 以{}为准", productId, useDbAsSource ? "数据库" : "Redis");

        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品不存在: {}", productId);
            return;
        }

        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        String boughtKey = Constants.REDIS_USER_BOUGHT_KEY + productId;

        if (useDbAsSource) {
            // 以数据库为准，修复Redis
            repairRedisFromDb(product, stockKey, boughtKey);
        } else {
            // 以Redis为准，修复数据库
            repairDbFromRedis(product, stockKey);
        }

        log.info("商品 {} 库存一致性修复完成", productId);
    }

    @Override
    public void repairAllConsistency() {
        log.info("========== 开始全量库存一致性修复（以数据库为准）==========");

        List<Product> products = productMapper.selectList(null);
        int repairedCount = 0;

        for (Product product : products) {
            ConsistencyCheckResult result = checkConsistency(product.getId());
            if (!result.isConsistent()) {
                repairConsistency(product.getId(), true);
                repairedCount++;
            }
        }

        log.info("========== 一致性修复完成, 共修复 {} 个商品 ==========", repairedCount);
    }

    /**
     * 以数据库为准，修复Redis
     */
    private void repairRedisFromDb(Product product, String stockKey, String boughtKey) {
        Long productId = product.getId();

        // 1. 从订单表计算每个用户的购买数量
        List<Order> orders = orderMapper.selectByProductId(productId);

        // 2. 重建Redis库存
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getAvailableStock()));
        log.info("修复Redis库存: productId={}, stock={}", productId, product.getAvailableStock());

        // 3. 重建用户购买记录
        redisTemplate.delete(boughtKey);
        for (Order order : orders) {
            if (order.getStatus() != Constants.ORDER_STATUS_CANCELLED) {
                redisTemplate.opsForHash().increment(boughtKey, order.getUserId().toString(), order.getQuantity());
            }
        }
        log.info("修复Redis购买记录: productId={}, 订单数={}", productId, orders.size());
    }

    /**
     * 以Redis为准，修复数据库
     */
    private void repairDbFromRedis(Product product, String stockKey) {
        Object redisStockObj = redisTemplate.opsForValue().get(stockKey);
        if (redisStockObj == null) {
            log.warn("Redis库存不存在，无法修复数据库");
            return;
        }

        int redisStock = parseInteger(redisStockObj);
        
        // 更新数据库库存
        product.setAvailableStock(redisStock);
        productMapper.updateById(product);
        
        log.info("修复数据库库存: productId={}, stock={}", product.getId(), redisStock);
    }

    /**
     * 计算数据库中已售数量（从订单表统计）
     */
    private int calculateDbSoldCount(Long productId) {
        List<Order> orders = orderMapper.selectByProductId(productId);
        return orders.stream()
                .filter(o -> o.getStatus() != Constants.ORDER_STATUS_CANCELLED)
                .mapToInt(Order::getQuantity)
                .sum();
    }

    /**
     * 计算Redis中已售数量（从购买记录Hash统计）
     */
    private int calculateRedisSoldCount(Long productId) {
        String boughtKey = Constants.REDIS_USER_BOUGHT_KEY + productId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(boughtKey);
        
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        return entries.values().stream()
                .mapToInt(this::parseInteger)
                .sum();
    }

    /**
     * 解析整数（处理各种格式）
     */
    private int parseInteger(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        if (obj instanceof Long) {
            return ((Long) obj).intValue();
        }
        String str = obj.toString().replace("\"", "");
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

