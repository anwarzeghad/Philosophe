package com.philosophers.philosopher_service.events;

import com.philosophers.philosopher_service.dto.EventDto;
import com.philosophers.philosopher_service.protocol.PeerUrls;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Adresses des pairs configurables et moniteur indisponible : un philosophe ne doit jamais être bloqué. */
class PublisherResilienceTest {

    @Test
    void peerUrls_defaultIsLocalhostWithOnePortPerPhilosopher() {
        PeerUrls urls = new PeerUrls(new MockEnvironment());
        assertEquals("http://localhost:9000", urls.of(0));
        assertEquals("http://localhost:9003", urls.of(3));
    }

    @Test
    void peerUrls_customBasePort() {
        PeerUrls urls = new PeerUrls(new MockEnvironment().withProperty("philosophers.base-port", "19000"));
        assertEquals("http://localhost:19002", urls.of(2));
    }

    @Test
    void peerUrls_kubernetesTemplateUsesHostnames() {
        PeerUrls urls = new PeerUrls(new MockEnvironment()
                .withProperty("philosophers.peer-url-template", "http://philosopher-{id}.philosopher:8080"));
        assertEquals("http://philosopher-4.philosopher:8080", urls.of(4));
    }

    @Test
    void httpPublisher_unreachableMonitor_doesNotThrow() {
        HttpEventPublisher publisher = new HttpEventPublisher(new RestTemplate(), "http://localhost:1");
        assertDoesNotThrow(() -> publisher.publish(new EventDto(0, "HUNGRY", 1)));
    }

    @Test
    void kafkaPublisher_brokerDown_doesNotThrowNorBlockLong() {
        var props = new java.util.HashMap<String, Object>();
        props.put("bootstrap.servers", "localhost:1");   // aucun broker
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.springframework.kafka.support.serializer.JacksonJsonSerializer");
        props.put("max.block.ms", "500");
        var factory = new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, EventDto>(props);
        var publisher = new KafkaEventPublisher(new org.springframework.kafka.core.KafkaTemplate<>(factory), "t");

        long start = System.currentTimeMillis();
        assertDoesNotThrow(() -> publisher.publish(new EventDto(0, "HUNGRY", 1)));
        assertTrue(System.currentTimeMillis() - start < 5000, "l'envoi ne doit pas bloquer le philosophe");
        factory.destroy();
    }
}
