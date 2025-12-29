package org.example.flash_sale.service.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.mapper.OrderMapper;
import org.example.flash_sale.service.OrderTimeoutService;
import org.example.flash_sale.service.ProductService;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订单超时处理服务实现
 * 使用 Redisson 延迟队列实现订单超时自动取消
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final RedissonClient redissonClient;
    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> stockRollbackScript;

    /**
     * 订单超时时间（分钟）
     */
    @Value("${order.timeout.minutes:30}")
    private int timeoutMinutes;

    /**
     * 是否启用订单超时功能
     */
    @Value("${order.timeout.enabled:true}")
    private boolean enabled;

    /**
     * 阻塞队列（用于消费）
     */
    private RBlockingQueue<String> blockingQueue;

    /**
     * 延迟队列（用于添加延迟任务）
     */
    private RDelayedQueue<String> delayedQueue;

    /**
     * 消费者线程池
     */
    private ExecutorService executorService;

    /**
     * 消费者运行标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("订单超时自动取消功能已禁用");
            return;
        }

        // 初始化队列
        blockingQueue = redissonClient.getBlockingQueue(Constants.ORDER_TIMEOUT_QUEUE);
        delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        log.info("订单超时服务初始化完成，超时时间: {}分钟", timeoutMinutes);

        // 启动消费者
        startConsumer();
    }

    @PreDestroy
    public void destroy() {
        stopConsumer();
        if (delayedQueue != null) {
            delayedQueue.destroy();
        }
    }

    @Override
    public void addOrderToTimeoutQueue(String orderNo) {
        if (!enabled) {
            return;
        }

        try {
            // 将订单号加入延迟队列，超时后自动进入阻塞队列
            delayedQueue.offer(orderNo, timeoutMinutes, TimeUnit.MINUTES);
            log.info("订单已加入超时队列: orderNo={}, 超时时间={}分钟", orderNo, timeoutMinutes);
        } catch (Exception e) {
            log.error("订单加入超时队列失败: orderNo={}", orderNo, e);
        }
    }

    @Override
    public void removeOrderFromTimeoutQueue(String orderNo) {
        if (!enabled) {
            return;
        }

        try {
            // 从延迟队列中移除订单（支付成功后调用）
            boolean removed = delayedQueue.remove(orderNo);
            if (removed) {
                log.info("订单已从超时队列移除: orderNo={}", orderNo);
            } else {
                // 可能已经进入阻塞队列，尝试从阻塞队列移除
                removed = blockingQueue.remove(orderNo);
                if (removed) {
                    log.info("订单已从阻塞队列移除: orderNo={}", orderNo);
                }
            }
        } catch (Exception e) {
            log.error("订单从超时队列移除失败: orderNo={}", orderNo, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTimeoutOrder(String orderNo) {
        log.info("处理超时订单: orderNo={}", orderNo);

        try {
            // 1. 查询订单
            Order order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.warn("超时订单不存在: orderNo={}", orderNo);
                return;
            }

            // 2. 检查订单状态（只处理待支付订单）
            if (order.getStatus() != Constants.ORDER_STATUS_PENDING) {
                log.info("订单非待支付状态，跳过取消: orderNo={}, status={}", orderNo, order.getStatus());
                return;
            }

            // 3. 更新订单状态为已取消
            order.setStatus(Constants.ORDER_STATUS_CANCELLED);
            order.setUpdateTime(LocalDateTime.now());
            int rows = orderMapper.updateById(order);

            if (rows > 0) {
                // 4. 回滚数据库库存
                productService.rollbackStock(order.getProductId(), order.getQuantity());

                // 5. 回滚Redis库存
                rollbackRedisStock(order.getProductId(), order.getUserId(), order.getQuantity());

                log.info("超时订单取消成功: orderNo={}, productId={}, quantity={}",
                        orderNo, order.getProductId(), order.getQuantity());
            } else {
                log.warn("超时订单状态更新失败: orderNo={}", orderNo);
            }
        } catch (Exception e) {
            log.error("处理超时订单异常: orderNo={}", orderNo, e);
            throw e; // 抛出异常触发事务回滚
        }
    }

    @Override
    public void startConsumer() {
        if (!enabled) {
            return;
        }

        if (running.compareAndSet(false, true)) {
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "order-timeout-consumer");
                t.setDaemon(true);
                return t;
            });

            executorService.submit(this::consumeTimeoutOrders);
            log.info("订单超时消费者已启动");
        }
    }

    @Override
    public void stopConsumer() {
        if (running.compareAndSet(true, false)) {
            if (executorService != null) {
                executorService.shutdownNow();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("订单超时消费者未能正常关闭");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("订单超时消费者已停止");
        }
    }

    /**
     * 消费超时订单（阻塞式）
     */
    private void consumeTimeoutOrders() {
        log.info("订单超时消费者开始监听...");

        while (running.get()) {
            try {
                // 阻塞等待超时订单（最多等待1秒，然后检查running状态）
                String orderNo = blockingQueue.poll(1, TimeUnit.SECONDS);
                
                if (orderNo != null) {
                    // 处理超时订单
                    try {
                        handleTimeoutOrder(orderNo);
                    } catch (Exception e) {
                        log.error("处理超时订单失败: orderNo={}", orderNo, e);
                        // 可以考虑重新加入队列或记录失败订单
                    }
                }
            } catch (InterruptedException e) {
                log.info("订单超时消费者被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("订单超时消费者异常", e);
                // 短暂休眠后继续
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("订单超时消费者退出");
    }

    /**
     * 回滚Redis库存
     */
    private void rollbackRedisStock(Long productId, Long userId, Integer quantity) {
        String stockKey = Constants.REDIS_STOCK_KEY + productId;
        String boughtKey = Constants.REDIS_USER_BOUGHT_KEY + productId;

        try {
            redisTemplate.execute(
                    stockRollbackScript,
                    Arrays.asList(stockKey, boughtKey),
                    userId.toString(),
                    quantity.toString()
            );
            log.debug("Redis库存回滚成功: productId={}, userId={}, quantity={}",
                    productId, userId, quantity);
        } catch (Exception e) {
            log.error("Redis库存回滚失败: productId={}, userId={}, quantity={}",
                    productId, userId, quantity, e);
        }
    }
}

