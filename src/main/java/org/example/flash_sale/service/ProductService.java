package org.example.flash_sale.service;

import org.example.flash_sale.entity.Product;

import java.util.List;

/**
 * 商品服务接口
 */
public interface ProductService {

    /**
     * 获取所有秒杀商品
     */
    List<Product> getAllFlashProducts();

    /**
     * 根据ID获取商品
     */
    Product getById(Long id);

    /**
     * 预热商品数据到Redis
     */
    void warmUpProductCache(Long productId);

    /**
     * 批量预热所有秒杀商品
     */
    void warmUpAllProducts();

    /**
     * 扣减数据库库存（使用乐观锁）
     */
    boolean deductStock(Long productId, Integer quantity);

    /**
     * 回滚库存
     */
    boolean rollbackStock(Long productId, Integer quantity);
}

