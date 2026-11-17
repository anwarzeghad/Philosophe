package com.philosophers.philosopher_service.events;

import com.philosophers.philosopher_service.dto.EventDto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Les philosophes publient leurs événements dans un vrai broker Kafka (embarqué). */
@SpringBootTest(properties = {
        "monitor.transport=kafka",
        "monitor.topic=philosophers-events",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        // mêmes délais que application.properties : la configuration réelle doit être valide
        "spring.kafka.producer.properties.max.block.ms=2000",
        "spring.kafka.producer.properties.request.timeout.ms=5000",
        "spring.kafka.producer.properties.delivery.timeout.ms=10000"
})
@EmbeddedKafka(partitions = 1, topics = "philosophers-events")
class KafkaEventPublisherTest {

    @Autowired
    EventPublisher publisher;
    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void publisherIsTheKafkaOne() {
        assertInstanceOf(KafkaEventPublisher.class, publisher);
    }

    @Test
    void event_isPublishedWithPhilosopherIdAsKeyAndJsonValue() {
        publisher.publish(new EventDto(4, "HUNGRY", 17));

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "test-group", true);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "philosophers-events");
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "philosophers-events", java.time.Duration.ofSeconds(15));
            assertEquals("4", record.key(), "la clé garde l'ordre des événements d'un philosophe");
            assertTrue(record.value().contains("\"state\":\"HUNGRY\""), record.value());
            assertTrue(record.value().contains("\"logicalClock\":17"), record.value());
        }
    }
}
