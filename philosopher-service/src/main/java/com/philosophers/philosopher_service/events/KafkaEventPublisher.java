package com.philosophers.philosopher_service.events;

import com.philosophers.philosopher_service.dto.EventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Transport asynchrone (partie 6) : l'événement est publié dans un topic Kafka, le moniteur le
 * consomme quand il peut. Le philosophe n'attend pas le moniteur, et si le moniteur est arrêté
 * les événements restent dans Kafka jusqu'à son retour (voir TP Kafka 1.6).
 *
 * La clé du message est l'identifiant du philosophe : Kafka garde l'ordre des événements
 * d'un même philosophe (même partition).
 */
@Component
@ConditionalOnProperty(name = "monitor.transport", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, EventDto> kafka;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, EventDto> kafka,
                               @Value("${monitor.topic:philosophers-events}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @Override
    public void publish(EventDto event) {
        try {
            kafka.send(topic, String.valueOf(event.philosopherId()), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            System.err.println("Échec d'envoi Kafka (" + event.state() + ") : " + error.getMessage());
                        }
                    });
        } catch (Exception e) {
            System.err.println("Kafka indisponible : " + e.getMessage());
        }
    }
}
