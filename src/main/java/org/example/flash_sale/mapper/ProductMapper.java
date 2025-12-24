package org.example.flash_sale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.flash_sale.entity.Product;

/**
 * 商品Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 使用乐观锁扣减库存
     * 只有在version匹配且库存充足时才会扣减成功
     */
    @Update("UPDATE t_product SET available_stock = available_stock - #{quantity}, " +
            "version = version + 1 " +
            "WHERE id = #{productId} AND version = #{version} AND available_stock >= #{quantity}")
    int deductStockWithOptimisticLock(@Param("productId") Long productId,
                                       @Param("quantity") Integer quantity,
                                       @Param("version") Integer version);

    /**
     * 直接扣减库存（不使用乐观锁，用于Redis已经扣减成功的场景）
     */
    @Update("UPDATE t_product SET available_stock = available_stock - #{quantity} " +
            "WHERE id = #{productId} AND available_stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 回滚库存
     */
    @Update("UPDATE t_product SET available_stock = available_stock + #{quantity} " +
            "WHERE id = #{productId}")
    int rollbackStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}

