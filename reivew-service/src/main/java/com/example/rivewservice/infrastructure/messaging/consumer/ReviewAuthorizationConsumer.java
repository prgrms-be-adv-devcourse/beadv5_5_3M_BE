package com.example.rivewservice.infrastructure.messaging.consumer;


import com.example.rivewservice.common.exception.ReviewErrorCode;
import com.example.rivewservice.common.exception.UserErrorCode;
import com.example.rivewservice.domain.model.Review;
import com.example.rivewservice.domain.model.User;
import com.example.rivewservice.domain.repository.ReviewRepository;
import com.example.rivewservice.domain.repository.UserRepository;
import com.example.rivewservice.infrastructure.messaging.dto.request.ReviewAuthorizationMessage;
import com.example.rivewservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAuthorizationConsumer {
    private final ReviewRepository reviewRepository;
    private final KafkaMessageUtil kafkaMessageUtil;
    private final UserRepository userRepository;

    @KafkaListener(topics = "ticket.review.authorized", groupId = "review-service")
    public void reviewAuthorizedConsumer(String message) {
        log.debug("[ticket.review.authorized] 수신: {}", message);
        ReviewAuthorizationMessage reviewAuthorizationMessage = kafkaMessageUtil.deserialize(message, ReviewAuthorizationMessage.class);

        User user = userRepository.findById(reviewAuthorizationMessage.userId())
                .orElseThrow(()->UserErrorCode.USER_NOT_FOUND.of(reviewAuthorizationMessage.userId()));

        if (user.getFlag()) {
            log.warn("[ticket.review.authorized] {}", UserErrorCode.USER_DELETED.message(reviewAuthorizationMessage.userId()));
            return;
        }

        if (reviewRepository.existsByTicketId(reviewAuthorizationMessage.ticketId())) {
            log.warn("[ticket.review.authorized] {}", ReviewErrorCode.REVIEW_TICKET_ALREADY_AUTHORIZED.message(reviewAuthorizationMessage.ticketId()));
            return;
        }

        Review review = Review.authorization(
                user,
                reviewAuthorizationMessage.movieId(),
                reviewAuthorizationMessage.scheduleId(),
                reviewAuthorizationMessage.ticketId()
        );

        reviewRepository.save(review);
    }
}
