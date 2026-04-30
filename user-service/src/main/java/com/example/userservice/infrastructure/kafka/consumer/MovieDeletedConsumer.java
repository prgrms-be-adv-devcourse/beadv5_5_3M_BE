package com.example.userservice.infrastructure.kafka.consumer;

import com.example.userservice.domain.repository.LikeRepository;
import com.example.userservice.infrastructure.kafka.KafkaUtil;
import com.example.userservice.infrastructure.kafka.consumer.dto.MovieDeletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieDeletedConsumer {

    private final LikeRepository likeRepository;
    private final KafkaUtil kafkaUtil;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "movie.deleted",
            groupId = "user-service"
    )
    @Transactional
    public void consume(String message) {
        log.info("[movie.deleted] 메시지 수신 - message: {}", message);
        MovieDeletedMessage msg = kafkaUtil.deserialize(message, MovieDeletedMessage.class);

        likeRepository.deleteByMovieId(msg.movieId());
        log.info("[movie.deleted] 좋아요 삭제 완료 - movieId: {}", msg.movieId());
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] movie.deleted 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
