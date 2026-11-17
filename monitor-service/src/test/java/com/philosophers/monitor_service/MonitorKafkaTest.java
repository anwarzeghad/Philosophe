package com.philosophers.monitor_service;

import com.philosophers.monitor_service.controller.EventStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le moniteur consomme les événements des philosophes depuis un vrai broker Kafka (embarqué).
 * Les messages sont ceux que produit KafkaEventPublisher : clé = id du philosophe, valeur = JSON.
 */
@SpringBootTest(properties = {
        "monitor.kafka.enabled=true",
        "monitor.topic=philosophers-events",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = "philosophers-events")
class MonitorKafkaTest {

    @Autowired
    EmbeddedKafkaBroker broker;
    @Autowired
    EventStore store;
    @Autowired
    MeterRegistry metrics;

    @BeforeEach
    void clean() {
        store.reset();
    }

    private void send(String key, String json) throws Exception {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        try (KafkaProducer<String, String> producer =
                     new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>("philosophers-events", key, json)).get();
        }
    }

    private void awaitEvents(int count) throws InterruptedException {
        long end = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < end) {
            if (store.history().values().stream().mapToInt(java.util.List::size).sum() >= count) {
                return;
            }
            Thread.sleep(100);
        }
        fail("événements Kafka non reçus par le moniteur");
    }

    @Test
    void eventsPublishedInKafka_areConsumedByTheMonitor() throws Exception {
        send("3", "{\"philosopherId\":3,\"state\":\"HUNGRY\",\"logicalClock\":1}");
        send("3", "{\"philosopherId\":3,\"state\":\"EATING\",\"logicalClock\":2}");
        send("1", "{\"philosopherId\":1,\"state\":\"HUNGRY\",\"logicalClock\":1}");
        awaitEvents(3);

        assertEquals("EATING", store.currentState().get(3L).state());
        assertEquals("HUNGRY", store.currentState().get(1L).state());
        assertEquals(2, store.history().get(3L).size());
        assertTrue(metrics.counter("philosophers.events", "state", "HUNGRY").count() >= 2,
                "la métrique Prometheus compte les événements");
    }

    @Test
    void historyIsOrderedByLogicalClock_notByArrival() throws Exception {
        send("2", "{\"philosopherId\":2,\"state\":\"DONE\",\"logicalClock\":3}");
        send("2", "{\"philosopherId\":2,\"state\":\"HUNGRY\",\"logicalClock\":1}");
        send("2", "{\"philosopherId\":2,\"state\":\"EATING\",\"logicalClock\":2}");
        awaitEvents(3);

        var states = store.history().get(2L).stream().map(e -> e.state()).toList();
        assertEquals(java.util.List.of("HUNGRY", "EATING", "DONE"), states);
        assertEquals("DONE", store.currentState().get(2L).state());
    }

    @Test
    void malformedMessage_doesNotStopTheConsumer() throws Exception {
        send("9", "ceci n'est pas du JSON");
        send("4", "{\"philosopherId\":4,\"state\":\"HUNGRY\",\"logicalClock\":1}");
        awaitEvents(1);
        assertEquals("HUNGRY", store.currentState().get(4L).state());
    }
}
