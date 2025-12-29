package org.example.flash_sale.service;

/**
 * 订单超时处理服务接口
 * 使用 Redisson 延迟队列实现订单超时自动取消
 */
public interface OrderTimeoutService {

    /**
     * 添加订单到超时延迟队列
     * 订单创建后调用，订单将在指定时间后自动取消
     *
     * @param orderNo 订单号
     */
    void addOrderToTimeoutQueue(String orderNo);

    /**
     * 从超时队列中移除订单
     * 订单支付成功后调用，避免已支付订单被取消
     *
     * @param orderNo 订单号
     */
    void removeOrderFromTimeoutQueue(String orderNo);

    /**
     * 处理超时订单
     * 由消费者调用，执行订单取消和库存回滚
     *
     * @param orderNo 订单号
     */
    void handleTimeoutOrder(String orderNo);

    /**
     * 启动延迟队列消费者
     * 应用启动时调用
     */
    void startConsumer();

    /**
     * 停止延迟队列消费者
     * 应用关闭时调用
     */
    void stopConsumer();
}

