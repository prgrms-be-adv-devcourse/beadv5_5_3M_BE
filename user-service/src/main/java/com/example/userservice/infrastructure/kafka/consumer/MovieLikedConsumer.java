package com.example.userservice.infrastructure.kafka.consumer;

import com.example.userservice.domain.model.Like;
import com.example.userservice.domain.repository.LikeRepository;
import com.example.userservice.infrastructure.kafka.KafkaUtil;
import com.example.userservice.infrastructure.kafka.consumer.dto.MovieLikedMessage;
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
public class MovieLikedConsumer {

    private final LikeRepository likeRepository;
    private final KafkaUtil kafkaUtil;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "movie.liked",
            groupId = "user-service"
    )
    @Transactional
    public void movieLikedConsumer(String message) {
        log.info("[movie.liked] 메시지 수신 - message: {}", message);
        MovieLikedMessage msg = kafkaUtil.deserialize(message, MovieLikedMessage.class);

        if ("LIKED".equals(msg.action())) {
            if (likeRepository.existsByMovieIdAndUserId(msg.movieId(), msg.userId())) {
                log.info("[movie.liked] 이미 좋아요한 영화 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
                return;
            }
            Like like = Like.create(msg.movieId(), msg.userId(), msg.imageUrl(), msg.title());
            likeRepository.save(like);
            log.info("[movie.liked] 좋아요 저장 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
        } else if ("UNLIKED".equals(msg.action())) {
            likeRepository.deleteByMovieIdAndUserId(msg.movieId(), msg.userId());
            log.info("[movie.liked] 좋아요 취소 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] movie.liked 처리 최종 실패 - 수동 처리 필요. message: {}", message);
    }
}
