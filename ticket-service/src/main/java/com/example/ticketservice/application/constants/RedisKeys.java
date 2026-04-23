package com.example.ticketservice.application.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeys {

    public static final String STOCK      = "stock:schedule:";
    public static final String QUEUE      = "queue:schedule:";
    public static final String PAYING     = "paying:schedule:";
    public static final String SEATS      = "seats:schedule:";
    public static final String COOKIE     = "cookie:schedule:";
    public static final String START_TIME = "startTime:schedule:";
    public static final String CART_COUNT = "cart:count:schedule:";
}
