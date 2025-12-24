package org.example.flash_sale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.example.flash_sale.common.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka配置类
 */
@Configuration
public class KafkaConfig {

    /**
     * 创建订单Topic
     * 分区数设置为6，便于并行消费
     * 副本数设置为1（生产环境建议至少3）
     */
    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(Constants.KAFKA_TOPIC_ORDER)
                .partitions(6)
                .replicas(1)
                .build();
    }
}

