package org.example.flash_sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.mapper.ProductMapper;
import org.example.flash_sale.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<Product> getAllFlashProducts() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getDeleted, 0)
               .orderByAsc(Product::getStartTime);
        return productMapper.selectList(wrapper);
    }

    @Override
    public Product getById(Long id) {
        // 优先从缓存获取
        String cacheKey = Constants.REDIS_PRODUCT_KEY + id;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Product) cached;
        }
        
        // 缓存未命中，从数据库获取
        Product product = productMapper.selectById(id);
        if (product != null) {
            // 缓存商品信息
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        }
        return product;
    }

    @Override
    public void warmUpProductCache(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品不存在: {}", productId);
            return;
        }

        // 缓存商品信息
        String productKey = Constants.REDIS_PRODUCT_KEY + productId;
        redisTemplate.opsForValue().set(productKey, product, 24, TimeUnit.HOURS);

        // 缓存库存信息
        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        redisTemplate.opsForValue().set(stockKey, product.getAvailableStock());

        log.info("商品缓存预热完成: productId={}, stock={}", productId, product.getAvailableStock());
    }

    @Override
    public void warmUpAllProducts() {
        List<Product> products = getAllFlashProducts();
        for (Product product : products) {
            // 只预热即将开始或正在进行的活动商品
            LocalDateTime now = LocalDateTime.now();
            if (product.getEndTime().isAfter(now)) {
                warmUpProductCache(product.getId());
            }
        }
        log.info("所有秒杀商品缓存预热完成，共{}个商品", products.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long productId, Integer quantity) {
        int rows = productMapper.deductStock(productId, quantity);
        if (rows > 0) {
            log.info("数据库库存扣减成功: productId={}, quantity={}", productId, quantity);
            return true;
        }
        log.warn("数据库库存扣减失败: productId={}, quantity={}", productId, quantity);
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long productId, Integer quantity) {
        int rows = productMapper.rollbackStock(productId, quantity);
        if (rows > 0) {
            log.info("数据库库存回滚成功: productId={}, quantity={}", productId, quantity);
            return true;
        }
        return false;
    }
}

