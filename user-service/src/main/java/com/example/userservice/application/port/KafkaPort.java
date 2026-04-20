package com.example.userservice.application.port;

public interface KafkaPort {

    void publish(String topic, String key, Object payload);
}
