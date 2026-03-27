package com.example.rivewservice.application.port.out;

public interface ReviewEventPublisherPort {
    void publish(String topic, String key, Object payload);
}
