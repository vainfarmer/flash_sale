package org.example.flash_sale.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Result;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/flash/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<Order> getOrder(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        
        // 检查订单归属
        if (!order.getUserId().equals(userId)) {
            return Result.fail(403, "无权查看此订单");
        }
        
        return Result.success(order);
    }

    /**
     * 查询用户订单列表
     */
    @GetMapping("/list")
    public Result<List<Order>> getUserOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Order> orders = orderService.getUserOrders(userId);
        return Result.success(orders);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel/{orderNo}")
    public Result<String> cancelOrder(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        
        boolean success = orderService.cancelOrder(orderNo, userId);
        if (success) {
            return Result.success("订单取消成功");
        }
        return Result.fail("订单取消失败");
    }

    /**
     * 支付订单
     */
    @PostMapping("/pay/{orderNo}")
    public Result<String> payOrder(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        
        boolean success = orderService.payOrder(orderNo, userId);
        if (success) {
            return Result.success("支付成功");
        }
        return Result.fail("支付失败");
    }
}

