package com.philosophers.philosopher_service.protocol;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Adresse HTTP d'un autre philosophe. Le modèle est configurable pour passer de la machine
 * locale (un port par philosophe) à Docker/Kubernetes (un nom d'hôte par philosophe) :
 *
 *   philosophers.peer-url-template=http://localhost:{port}                  (défaut, port = base-port + id)
 *   philosophers.peer-url-template=http://philosopher-{id}.philosopher:8080  (Kubernetes, StatefulSet)
 */
@Component
public class PeerUrls {

    private final String template;
    private final int basePort;

    public PeerUrls(Environment env) {
        this.template = env.getProperty("philosophers.peer-url-template", "http://localhost:{port}");
        this.basePort = env.getProperty("philosophers.base-port", Integer.class, 9000);
    }

    public String of(int peerId) {
        return template.replace("{id}", String.valueOf(peerId))
                .replace("{port}", String.valueOf(basePort + peerId));
    }
}
