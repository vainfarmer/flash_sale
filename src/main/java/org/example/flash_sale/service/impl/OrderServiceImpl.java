package org.example.flash_sale.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.mapper.OrderMapper;
import org.example.flash_sale.service.OrderService;
import org.example.flash_sale.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> stockRollbackScript;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(Order order) {
        // 先扣减数据库库存
        boolean stockDeducted = productService.deductStock(order.getProductId(), order.getQuantity());
        if (!stockDeducted) {
            log.error("数据库库存扣减失败，订单创建失败: {}", order.getOrderNo());
            return false;
        }

        // 插入订单
        int rows = orderMapper.insert(order);
        if (rows > 0) {
            log.info("订单创建成功: orderNo={}", order.getOrderNo());
            return true;
        }
        
        // 订单创建失败，回滚库存
        productService.rollbackStock(order.getProductId(), order.getQuantity());
        return false;
    }

    @Override
    public Order getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    @Override
    public List<Order> getUserOrders(Long userId) {
        return orderMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("订单不存在: {}", orderNo);
            return false;
        }

        if (!order.getUserId().equals(userId)) {
            log.warn("无权取消他人订单: orderNo={}, userId={}", orderNo, userId);
            return false;
        }

        if (order.getStatus() != Constants.ORDER_STATUS_PENDING) {
            log.warn("订单状态不允许取消: orderNo={}, status={}", orderNo, order.getStatus());
            return false;
        }

        // 更新订单状态
        order.setStatus(Constants.ORDER_STATUS_CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        int rows = orderMapper.updateById(order);
        
        if (rows > 0) {
            // 回滚数据库库存
            productService.rollbackStock(order.getProductId(), order.getQuantity());
            
            // 回滚Redis库存
            rollbackRedisStock(order.getProductId(), order.getUserId(), order.getQuantity());
            
            log.info("订单取消成功: orderNo={}", orderNo);
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("订单不存在: {}", orderNo);
            return false;
        }

        if (!order.getUserId().equals(userId)) {
            log.warn("无权支付他人订单");
            return false;
        }

        if (order.getStatus() != Constants.ORDER_STATUS_PENDING) {
            log.warn("订单状态不允许支付: orderNo={}, status={}", orderNo, order.getStatus());
            return false;
        }

        // 更新订单状态
        order.setStatus(Constants.ORDER_STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        int rows = orderMapper.updateById(order);
        
        if (rows > 0) {
            log.info("订单支付成功: orderNo={}", orderNo);
            return true;
        }
        return false;
    }

    /**
     * 回滚Redis库存
     */
    private void rollbackRedisStock(Long productId, Long userId, Integer quantity) {
        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        String boughtKey = Constants.REDIS_USER_BOUGHT_KEY + productId;
        
        redisTemplate.execute(
                stockRollbackScript,
                Arrays.asList(stockKey, boughtKey),
                userId.toString(),
                quantity.toString()
        );
    }
}

