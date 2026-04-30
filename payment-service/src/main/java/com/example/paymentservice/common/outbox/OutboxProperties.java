package com.example.paymentservice.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.outbox")
public class OutboxProperties {

    private int batchSize = 100;
    private long pollDelayMs = 500L;
    private long sendTimeoutMs = 3_000L;
    private int maxRetries = 5;
    private int backoffBaseSeconds = 10;

    public int batchSize() {
        return batchSize;
    }

    public long pollDelayMs() {
        return pollDelayMs;
    }

    public long sendTimeoutMs() {
        return sendTimeoutMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int backoffBaseSeconds() {
        return backoffBaseSeconds;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize > 0) this.batchSize = batchSize;
    }

    public void setPollDelayMs(long pollDelayMs) {
        if (pollDelayMs > 0) this.pollDelayMs = pollDelayMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        if (sendTimeoutMs > 0) this.sendTimeoutMs = sendTimeoutMs;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries > 0) this.maxRetries = maxRetries;
    }

    public void setBackoffBaseSeconds(int backoffBaseSeconds) {
        if (backoffBaseSeconds > 0) this.backoffBaseSeconds = backoffBaseSeconds;
    }
}
