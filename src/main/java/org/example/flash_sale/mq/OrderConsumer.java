package org.example.flash_sale.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.dto.OrderMessage;
import org.example.flash_sale.entity.Order;
import org.example.flash_sale.service.OrderService;
import org.example.flash_sale.service.OrderTimeoutService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者
 * 从Kafka消费订单消息，异步创建订单到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderService orderService;
    private final OrderTimeoutService orderTimeoutService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 消费订单消息
     * 使用手动确认模式，确保消息处理成功后再提交offset
     */
    @KafkaListener(
            topics = Constants.KAFKA_TOPIC_ORDER,
            groupId = "flash-sale-order-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderNo = record.key();
        log.info("收到订单消息: orderNo={}, partition={}, offset={}",
                orderNo, record.partition(), record.offset());

        try {
            OrderMessage message = objectMapper.readValue(record.value(), OrderMessage.class);
            
            // 幂等性检查：检查订单是否已存在
            Order existingOrder = orderService.getByOrderNo(orderNo);
            if (existingOrder != null) {
                log.warn("订单已存在，跳过处理: orderNo={}", orderNo);
                ack.acknowledge();
                return;
            }

            // 创建订单
            Order order = new Order();
            order.setOrderNo(message.getOrderNo());
            order.setUserId(message.getUserId());
            order.setProductId(message.getProductId());
            order.setProductName(message.getProductName());
            order.setQuantity(message.getQuantity());
            order.setAmount(message.getAmount());
            order.setStatus(Constants.ORDER_STATUS_PENDING);

            boolean success = orderService.createOrder(order);
            if (success) {
                log.info("订单创建成功: orderNo={}", orderNo);
                
                // 将订单加入超时延迟队列（超时未支付自动取消）
                orderTimeoutService.addOrderToTimeoutQueue(orderNo);
                
                // 手动确认消息
                ack.acknowledge();
            } else {
                log.error("订单创建失败: orderNo={}", orderNo);
                // 不确认消息，等待重试
                // 可以根据业务需求决定是否直接确认并记录失败订单
            }
        } catch (Exception e) {
            log.error("处理订单消息异常: orderNo={}", orderNo, e);
            // 异常情况下也确认消息，防止无限重试
            // 将异常订单记录到失败表中，通过补偿机制处理
            ack.acknowledge();
        }
    }
}

