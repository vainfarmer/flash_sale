package org.example.flash_sale.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.dto.OrderMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 订单消息生产者
 * 将秒杀成功的订单发送到Kafka，实现削峰填谷
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送订单消息
     * 使用订单号作为Key，保证同一订单的消息发送到同一分区
     */
    public void sendOrderMessage(OrderMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    Constants.KAFKA_TOPIC_ORDER,
                    message.getOrderNo(),  // 使用订单号作为Key
                    jsonMessage
            );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("订单消息发送成功: orderNo={}, partition={}, offset={}",
                            message.getOrderNo(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("订单消息发送失败: orderNo={}", message.getOrderNo(), ex);
                    // 发送失败的处理逻辑（可以存入本地表重试）
                    handleSendFailure(message, ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("订单消息序列化失败: {}", message, e);
        }
    }

    /**
     * 同步发送订单消息（需要确认发送结果的场景）
     */
    public boolean sendOrderMessageSync(OrderMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            SendResult<String, String> result = kafkaTemplate.send(
                    Constants.KAFKA_TOPIC_ORDER,
                    message.getOrderNo(),
                    jsonMessage
            ).get();
            
            log.info("订单消息同步发送成功: orderNo={}", message.getOrderNo());
            return true;
        } catch (Exception e) {
            log.error("订单消息同步发送失败: orderNo={}", message.getOrderNo(), e);
            return false;
        }
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailure(OrderMessage message, Throwable ex) {
        // 可以将消息存入本地数据库，通过定时任务重试
        log.error("需要重试的订单消息: {}", message);
    }
}

