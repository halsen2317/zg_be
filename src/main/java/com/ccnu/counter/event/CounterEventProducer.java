package com.ccnu.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** 计数事件 Kafka 生产者。 */
@Service
public class CounterEventProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public CounterEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka; this.objectMapper = objectMapper;
    }

    public void publish(CounterEvent event) {
        try {
            kafka.send(CounterTopics.EVENTS, objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {}
    }
}