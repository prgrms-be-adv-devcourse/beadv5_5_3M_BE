package com.example.ticketservice.infrastructure.messaging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    // inbound — from movie-service
    public static final String SCHEDULE_CONFIRMED = "movie.schedule.confirmed";

    // outbound — ticket lifecycle
    public static final String TICKET_PAID      = "ticket.paid";
    public static final String TICKET_REFUNDED  = "ticket.refunded";
    public static final String TICKET_PROVIDE   = "ticket.provide";

    // outbound — schedule lifecycle
    public static final String CART_CLOSED       = "cart.closed";
    public static final String TICKETING_STARTED = "ticketing.started";

    // outbound — review
    public static final String REVIEW_AUTHORIZED = "ticket.review.authorized";

    // internal — queue management
    public static final String QUEUE_DRAIN      = "queue.drain";
    public static final String QUEUE_TERMINATED = "queue.terminated";
}