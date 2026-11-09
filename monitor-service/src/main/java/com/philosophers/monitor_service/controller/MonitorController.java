package com.philosophers.monitor_service.controller;

import com.philosophers.monitor_service.dto.EventDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class MonitorController {

    @PostMapping
    public void receiveEvent(@RequestBody EventDto event) {
        System.out.println("LOG MONITEUR -> Philosophe " + event.philosopherId() 
            + " | État : " + event.state() 
            + " | Horloge : " + event.logicalClock());
    }
}