package com.philosophers.philosopher_service.events;

import com.philosophers.philosopher_service.dto.EventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Transport synchrone (parties 1 à 5) : POST /events sur le moniteur. */
@Component
@ConditionalOnProperty(name = "monitor.transport", havingValue = "http", matchIfMissing = true)
public class HttpEventPublisher implements EventPublisher {

    private final RestTemplate restTemplate;
    private final String monitorUrl;

    public HttpEventPublisher(RestTemplate restTemplate,
                              @Value("${monitor.url:http://localhost:8080}") String monitorUrl) {
        this.restTemplate = restTemplate;
        this.monitorUrl = monitorUrl;
    }

    @Override
    public void publish(EventDto event) {
        try {
            restTemplate.postForObject(monitorUrl + "/events", event, Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de contacter le moniteur : " + e.getMessage());
        }
    }
}
