package com.example.paymentservice.common.messaging;

public final class PaymentTopics {

    private PaymentTopics() {}

    public static final String PAYMENT_CONFIRMED = "payment.confirmed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
}
