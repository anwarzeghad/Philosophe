package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Points d'entrée HTTP par lesquels les autres philosophes envoient leurs messages. */
@RestController
@RequestMapping("/protocol")
public class ProtocolController {

    private final ForkProtocol protocol;

    public ProtocolController(ForkProtocol protocol) {
        this.protocol = protocol;
    }

    @PostMapping("/request")
    public void request(@RequestBody RequestMessage message) {
        protocol.onRequest(message);
    }

    @PostMapping("/finished")
    public void finished(@RequestBody FinishedMessage message) {
        protocol.onFinished(message);
    }

    @PostMapping("/token")
    public void token(@RequestBody TokenMessage message) {
        protocol.onToken(message);
    }
}
