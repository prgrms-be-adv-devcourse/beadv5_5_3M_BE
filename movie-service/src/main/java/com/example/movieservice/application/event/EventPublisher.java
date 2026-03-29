package com.example.movieservice.application.event;

public interface EventPublisher {
    void publish(String topic, String key, Object payload);
}
