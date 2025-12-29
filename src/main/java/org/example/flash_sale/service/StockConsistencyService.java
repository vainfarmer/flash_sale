package org.example.flash_sale.service;

/**
 * 库存一致性检验服务接口
 */
public interface StockConsistencyService {

    /**
     * 检查单个商品的库存一致性
     * @param productId 商品ID
     * @return 一致性检查结果
     */
    ConsistencyCheckResult checkConsistency(Long productId);

    /**
     * 检查所有秒杀商品的库存一致性
     */
    void checkAllProductsConsistency();

    /**
     * 修复单个商品的库存不一致
     * @param productId 商品ID
     * @param useDbAsSource 是否以数据库为准（true=以DB为准，false=以Redis为准）
     */
    void repairConsistency(Long productId, boolean useDbAsSource);

    /**
     * 修复所有商品的库存不一致（以数据库为准）
     */
    void repairAllConsistency();

    /**
     * 一致性检查结果
     */
    record ConsistencyCheckResult(
            Long productId,
            String productName,
            Integer dbStock,
            Integer redisStock,
            Integer dbSoldCount,
            Integer redisSoldCount,
            boolean isConsistent,
            String message
    ) {}
}

