package com.example.creatorservice.application.service;

import com.example.creatorservice.application.exception.MovieException;
import com.example.creatorservice.application.usecase.MovieUploadUseCase;
import com.example.creatorservice.domain.model.Category;
import com.example.creatorservice.domain.model.Movie;
import com.example.creatorservice.domain.model.Movie.Visibility;
import com.example.creatorservice.domain.repository.CategoryRepository;
import com.example.creatorservice.domain.repository.CreatorRepository;
import com.example.creatorservice.domain.repository.MovieRepository;
import com.example.creatorservice.infrastructure.kafka.MovieEventPublisher;
import com.example.creatorservice.infrastructure.kafka.dto.MovieAiCreatedMessage;
import com.example.creatorservice.infrastructure.kafka.dto.MovieAiUpdatedMessage;
import com.example.creatorservice.infrastructure.kafka.dto.MovieDeletedMessage;
import com.example.creatorservice.infrastructure.kafka.dto.MovieUpdatedMessage;
import com.example.creatorservice.infrastructure.kafka.dto.MovieUploadedMessage;
import com.example.creatorservice.infrastructure.kafka.dto.MovieVisibilityChangedMessage;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiCreatedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiUpdatedEvent;
import com.example.creatorservice.infrastructure.storage.FfprobeResult;
import com.example.creatorservice.infrastructure.storage.FfprobeVideoValidator;
import com.example.creatorservice.infrastructure.storage.FileStorageService;
import com.example.creatorservice.infrastructure.storage.VideoProcessingService;
import com.example.creatorservice.presentation.dto.req.RegisterMovieRequest;
import com.example.creatorservice.presentation.dto.req.UpdateMovieDetailRequest;
import com.example.creatorservice.presentation.dto.req.UpdateVisibilityRequest;
import com.example.creatorservice.presentation.dto.res.MovieDetailResponse;
import com.example.creatorservice.presentation.dto.res.MovieListItemResponse;
import com.example.creatorservice.presentation.dto.res.SchedulableMovieResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieUploadService implements MovieUploadUseCase {

    private static final int MAX_MOVIES_PER_CREATOR = 3;
    private static final long BASE_COOKIE_UNIT_BYTES = 100L * 1024 * 1024; // 100MB

    private final MovieRepository movieRepository;
    private final CategoryRepository categoryRepository;
    private final CreatorRepository creatorRepository;
    private final FileStorageService fileStorageService;
    private final VideoProcessingService videoProcessingService;
    private final FfprobeVideoValidator ffprobeVideoValidator;
    private final MovieEventPublisher movieEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public Long register(UUID creatorId, RegisterMovieRequest request, MultipartFile image, MultipartFile video) {
        if (movieRepository.countByCreatorId(creatorId) >= MAX_MOVIES_PER_CREATOR) {
            throw MovieException.registrationLimitExceeded();
        }

        validateVideoExtension(video);
        validateImageExtension(image);

        String fileGroupId = UUID.randomUUID().toString();
        String safeImageName = UUID.randomUUID() + extractExtension(image.getOriginalFilename());
        String safeVideoName = UUID.randomUUID() + ".mp4";
        String imageRelativePath = null;
        String videoRelativePath = null;
        try {
            imageRelativePath = fileStorageService.store(image, "posters/" + fileGroupId, safeImageName);
            videoRelativePath = fileStorageService.store(video, "movies/" + fileGroupId, safeVideoName);
            String absoluteVideoPath = fileStorageService.resolveAbsolutePath(videoRelativePath);
            FfprobeResult ffprobeResult = ffprobeVideoValidator.validate(absoluteVideoPath);
            String finalVideoPath = videoProcessingService.process(videoRelativePath); // dev: .mp4, prod: .m3u8

            int baseCookie = calculateBaseCookie(video.getSize());
            Movie movie = Movie.create(
                    creatorId,
                    request.title(),
                    request.description(),
                    baseCookie,
                    request.additionalCookie(),
                    ffprobeResult.durationSeconds(),
                    imageRelativePath,
                    finalVideoPath
            );

            if (request.categoryIds() != null) {
                for (Long categoryId : request.categoryIds()) {
                    Category category = categoryRepository.findById(categoryId)
                            .orElseThrow(() -> MovieException.categoryNotFound(categoryId));
                    movie.addCategory(category);
                }
            }

            Movie saved = movieRepository.save(movie);
            log.info("[Movie] 영화 등록 완료 - movieId: {}, creatorId: {}", saved.getMovieId(), creatorId);

            applicationEventPublisher.publishEvent(new MovieAiCreatedEvent(
                    new MovieAiCreatedMessage(
                            saved.getMovieId(),
                            saved.getCategories().stream().map(Category::getName).toArray(String[]::new),
                            saved.getDescription()
                    )
            ));
            return saved.getMovieId();

        } catch (Exception e) {
            if (imageRelativePath != null) fileStorageService.delete(imageRelativePath);
            if (videoRelativePath != null) fileStorageService.delete(videoRelativePath);
            throw e;
        }
    }

    @Override
    @Transactional
    public void updateVisibility(UUID creatorId, Long movieId, UpdateVisibilityRequest request) {
        Movie movie = getAuthorizedMovie(creatorId, movieId);
        Visibility before = movie.getVisibility();
        Visibility after = request.visibility();

        if (before == after) {
            return;
        }

        if (after == Visibility.PRIVATE && movieRepository.existsConfirmedScheduleByMovieId(movieId)) {
            throw MovieException.alreadyScheduled();
        }

        movie.updateVisibility(after);

        if (after == Visibility.PUBLIC && !movie.isEverPublished()) {
            // 최초 공개 — ES 신규 색인 (풀 데이터)
            movie.markAsPublished();
            String creatorNickname = creatorRepository.findById(movie.getCreatorId()).getNickname();
            List<MovieUploadedMessage.CategoryInfo> categories = movie.getCategories().stream()
                    .map(c -> new MovieUploadedMessage.CategoryInfo(c.getCategoryId(), c.getName()))
                    .toList();
            movieEventPublisher.publishMovieUploaded(
                    new MovieUploadedMessage(movie.getMovieId(), movie.getTitle(), movie.getDescription(), movie.getCreatorId(), creatorNickname, categories)
            );
        } else {
            // 이후 visibility 변경 — ES visibility 필드만 업데이트
            movieEventPublisher.publishMovieVisibilityChanged(
                    new MovieVisibilityChangedMessage(movie.getMovieId(), after.name())
            );
        }

        applicationEventPublisher.publishEvent(new MovieAiUpdatedEvent(
                new MovieAiUpdatedMessage(
                        movieId,
                        List.of("visibility"),
                        null,
                        null,
                        after.name()
                )
        ));
    }

    @Override
    @Transactional
    public void updateDetail(UUID creatorId, Long movieId, UpdateMovieDetailRequest request) {
        Movie movie = getAuthorizedMovie(creatorId, movieId);

        if (movieRepository.existsConfirmedScheduleByMovieId(movieId)) {
            throw MovieException.alreadyScheduled();
        }

        // replaceCategories() 호출 전에 비교 (트랜잭션 안 — lazy loading 안전)
        boolean descriptionChanged = !movie.getDescription().equals(request.description());
        Set<Long> beforeCategoryIds = movie.getCategories().stream()
                .map(Category::getCategoryId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> afterCategoryIds = new HashSet<>(request.categoryIds());
        boolean categoryChanged = !beforeCategoryIds.equals(afterCategoryIds);

        movie.updateDetail(request.title(), request.description(), request.additionalCookie());

        List<Category> newCategories = request.categoryIds().stream()
                .map(categoryId -> categoryRepository.findById(categoryId)
                        .orElseThrow(() -> MovieException.categoryNotFound(categoryId)))
                .toList();
        movie.replaceCategories(newCategories);

        if (movie.isPublic()) {
            List<MovieUpdatedMessage.CategoryInfo> categories = movie.getCategories().stream()
                    .map(c -> new MovieUpdatedMessage.CategoryInfo(c.getCategoryId(), c.getName()))
                    .toList();
            movieEventPublisher.publishMovieUpdated(
                    new MovieUpdatedMessage(movie.getMovieId(), movie.getTitle(), movie.getDescription(), categories)
            );
        }

        // 실제 변경된 필드가 있을 때만 발행
        if (descriptionChanged || categoryChanged) {
            List<String> aiChangedFields = new ArrayList<>();
            if (descriptionChanged) aiChangedFields.add("description");
            if (categoryChanged) aiChangedFields.add("category");

            applicationEventPublisher.publishEvent(new MovieAiUpdatedEvent(
                    new MovieAiUpdatedMessage(
                            movieId,
                            aiChangedFields,
                            movie.getDescription(),
                            movie.getCategories().stream().map(Category::getName).toArray(String[]::new),
                            null
                    )
            ));
        }
    }

    @Override
    @Transactional
    public void delete(UUID creatorId, Long movieId) {
        Movie movie = getAuthorizedMovie(creatorId, movieId);

        if (movieRepository.existsConfirmedScheduleByMovieId(movieId)) {
            throw MovieException.alreadyScheduled();
        }

        boolean wasPublic = movie.isPublic();
        String imageUrl = movie.getImageUrl();
        String videoUrl = movie.getVideoUrl();

        movieRepository.delete(movie);

        if (imageUrl != null) fileStorageService.delete(imageUrl);
        if (videoUrl != null) fileStorageService.delete(videoUrl);

        movieEventPublisher.publishMovieDeleted(new MovieDeletedMessage(movie.getMovieId()));

        log.info("[Movie] 영화 삭제 완료 - movieId: {}, creatorId: {}", movieId, creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public MovieDetailResponse getDetail(UUID creatorId, Long movieId) {
        Movie movie = getAuthorizedMovie(creatorId, movieId);
        return MovieDetailResponse.from(movie);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovieListItemResponse> getMyMovies(UUID creatorId) {
        return movieRepository.findAllByCreatorId(creatorId).stream()
                .map(MovieListItemResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchedulableMovieResponse> getSchedulableMovies(UUID creatorId) {
        return movieRepository.findAllByCreatorId(creatorId).stream()
                .filter(Movie::isPublic)
                .map(SchedulableMovieResponse::from)
                .toList();
    }

    private Movie getAuthorizedMovie(UUID creatorId, Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(MovieException::notFound);
        if (!movie.getCreatorId().equals(creatorId)) {
            throw MovieException.forbidden();
        }
        return movie;
    }

    private void validateVideoExtension(MultipartFile video) {
        String filename = video.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".mp4")) {
            throw MovieException.invalidVideoFormat("mp4 형식의 영상 파일만 업로드할 수 있습니다.");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private int calculateBaseCookie(long fileSizeBytes) {
        long result = Math.max(1, (fileSizeBytes + BASE_COOKIE_UNIT_BYTES - 1) / BASE_COOKIE_UNIT_BYTES);
        return Math.toIntExact(result);
    }

    private void validateImageExtension(MultipartFile image) {
        String filename = image.getOriginalFilename();
        if (filename == null) {
            throw MovieException.invalidVideoFormat("이미지 파일명이 없습니다.");
        }
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")
                && !lower.endsWith(".png") && !lower.endsWith(".webp")) {
            throw MovieException.invalidVideoFormat("jpg, jpeg, png, webp 형식의 이미지 파일만 업로드할 수 있습니다.");
        }
    }
}