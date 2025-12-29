package org.example.flash_sale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.flash_sale.entity.Order;

import java.util.List;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据订单号查询订单
     */
    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo}")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询用户的秒杀订单
     */
    @Select("SELECT * FROM t_order WHERE user_id = #{userId} AND product_id = #{productId}")
    List<Order> selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 查询用户所有订单
     */
    @Select("SELECT * FROM t_order WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Order> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询所有待支付订单
     */
    @Select("SELECT * FROM t_order WHERE status = 0 AND deleted = 0")
    List<Order> selectPendingOrders();
}

