package com.example.movieservice.application.usecase;

import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewWrittenMessage;

public interface ReviewUseCase {
    void handleReviewWritten(ReviewWrittenMessage msg);
    void handleReviewUpdated(ReviewUpdatedMessage msg);
    void handleReviewDeleted(ReviewDeletedMessage msg);
}
