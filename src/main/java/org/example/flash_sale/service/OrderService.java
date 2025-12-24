package org.example.flash_sale.service;

import org.example.flash_sale.entity.Order;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     */
    boolean createOrder(Order order);

    /**
     * 根据订单号查询订单
     */
    Order getByOrderNo(String orderNo);

    /**
     * 查询用户的订单列表
     */
    List<Order> getUserOrders(Long userId);

    /**
     * 取消订单
     */
    boolean cancelOrder(String orderNo, Long userId);

    /**
     * 支付订单
     */
    boolean payOrder(String orderNo, Long userId);
}

