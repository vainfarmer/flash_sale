package org.example.flash_sale.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.common.Result;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.mapper.OrderMapper;
import org.example.flash_sale.service.OrderTimeoutService;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单超时管理接口（仅用于测试和管理）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/order-timeout")
@RequiredArgsConstructor
public class OrderTimeoutController {

    private final OrderTimeoutService orderTimeoutService;
    private final RedissonClient redissonClient;
    private final OrderMapper orderMapper;

    @Value("${order.timeout.minutes:30}")
    private int timeoutMinutes;

    @Value("${order.timeout.enabled:true}")
    private boolean enabled;

    /**
     * 获取超时队列状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(Constants.ORDER_TIMEOUT_QUEUE);
        RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", enabled);
        status.put("timeoutMinutes", timeoutMinutes);
        status.put("blockingQueueSize", blockingQueue.size());
        status.put("delayedQueueSize", delayedQueue.size());
        
        // 获取待处理的订单号列表（最多显示20个）
        List<String> pendingOrders = blockingQueue.stream()
                .limit(20)
                .collect(Collectors.toList());
        status.put("pendingOrders", pendingOrders);

        return Result.success(status);
    }

    /**
     * 手动将订单加入超时队列
     */
    @PostMapping("/add/{orderNo}")
    public Result<String> addOrder(@PathVariable String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        if (order.getStatus() != Constants.ORDER_STATUS_PENDING) {
            return Result.fail(400, "订单非待支付状态");
        }

        orderTimeoutService.addOrderToTimeoutQueue(orderNo);
        return Result.success("订单已加入超时队列");
    }

    /**
     * 手动从超时队列移除订单
     */
    @PostMapping("/remove/{orderNo}")
    public Result<String> removeOrder(@PathVariable String orderNo) {
        orderTimeoutService.removeOrderFromTimeoutQueue(orderNo);
        return Result.success("订单已从超时队列移除");
    }

    /**
     * 手动处理超时订单
     */
    @PostMapping("/process/{orderNo}")
    public Result<String> processOrder(@PathVariable String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }

        orderTimeoutService.handleTimeoutOrder(orderNo);
        return Result.success("订单超时处理完成");
    }

    /**
     * 批量处理所有超时订单（立即处理，不等待延迟）
     */
    @PostMapping("/process-all-pending")
    public Result<Map<String, Object>> processAllPending() {
        // 获取所有待支付订单
        List<Order> pendingOrders = orderMapper.selectPendingOrders();
        
        int processedCount = 0;
        int skippedCount = 0;
        
        for (Order order : pendingOrders) {
            try {
                orderTimeoutService.handleTimeoutOrder(order.getOrderNo());
                processedCount++;
            } catch (Exception e) {
                log.error("处理订单失败: {}", order.getOrderNo(), e);
                skippedCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalPending", pendingOrders.size());
        result.put("processed", processedCount);
        result.put("skipped", skippedCount);
        
        return Result.success(result);
    }
}

