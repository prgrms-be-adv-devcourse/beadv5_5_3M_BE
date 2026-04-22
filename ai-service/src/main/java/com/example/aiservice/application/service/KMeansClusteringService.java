package com.example.aiservice.application.service;

import com.example.aiservice.domain.model.ClusterCenter;
import com.example.aiservice.domain.model.MovieEmbedded;
import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.UserPreference;
import com.example.aiservice.domain.model.enums.InteractionType;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import com.example.aiservice.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KMeansClusteringService {

    @Value("${batch.kmeans.window-size}")
    private int windowSize;

    @Value("${batch.kmeans.k-max}")
    private int kMax;

    @Value("${batch.kmeans.cold-start-threshold}")
    private double coldStartThreshold;

    @Value("${batch.kmeans.watch-weight}")
    private int watchWeight;

    @Value("${batch.kmeans.like-weight}")
    private int likeWeight;

    private static final double ELBOW_THRESHOLD = 0.3; // 감소폭이 이전 단계의 몇 % 미만이면 엘보우로 판단할지를 결정하는 임계값 (실데이터 기반으로 검증 필요, 임의 설정)
    private static final int MAX_ITERATIONS = 100; //K-Means가 수렴하지 않을 때 강제로 종료하는 안전장치 (표준 관행)
    private static final double CONVERGENCE_TOLERANCE = 1e-4; //중심점이 얼마나 조금 움직이면 "수렴했다"고 판단할 거리² 기준 (표준 관향)

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserInteractionHistoryRepository userInteractionHistoryRepository;
    private final MovieEmbeddedRepository movieEmbeddedRepository;

    @Transactional
    public void recalculateClusters() {
        List<UserPreference> users = userPreferenceRepository.findAll();
        int updated = 0;

        for (UserPreference user : users) {
            try {
                processUser(user);
                updated++;
            } catch (Exception e) {
                log.warn("[K-Means] 유저 {} 처리 실패: {}", user.getUserId(), e.getMessage());
            }
        }
        log.info("[Batch] K-Means 클러스터 재계산 완료 - 처리 유저: {}명", updated);
    }

    private void processUser(UserPreference user) {
        List<UserInteractionHistory> interactions =
                userInteractionHistoryRepository.findRecentByUserId(user.getUserId(), windowSize);

        if (interactions.isEmpty()) {
            return;
        }

        // cold start: SUM(WATCH×watchWeight + LIKE×likeWeight) < coldStartThreshold
        double totalScore = interactions.stream()
                .mapToDouble(h -> h.getInteractionType() == InteractionType.WATCH ? watchWeight : likeWeight)
                .sum();

        if (totalScore < coldStartThreshold) {
            if (user.getCluster() != null) {
                userPreferenceRepository.save(user.withCluster(null));
            }
            return;
        }

        List<float[]> dataPoints = buildDataPoints(interactions);

        if (dataPoints.isEmpty()) {
            return;
        }

        KMeansResult result = findOptimalResult(dataPoints);
        List<ClusterCenter> centers = buildClusterCenters(result, dataPoints.size());
        userPreferenceRepository.save(user.withCluster(centers));
    }

    private List<float[]> buildDataPoints(List<UserInteractionHistory> interactions) {
        Set<Long> movieIds = interactions.stream()
                .map(UserInteractionHistory::getMovieId)
                .collect(Collectors.toSet());

        Map<Long, float[]> embeddingMap = movieEmbeddedRepository.findAllByIds(movieIds).stream()
                .filter(m -> m.getEmbedding() != null)
                .collect(Collectors.toMap(MovieEmbedded::getMovieId, MovieEmbedded::getEmbedding));

        List<float[]> points = new ArrayList<>();
        for (UserInteractionHistory h : interactions) {
            float[] embedding = embeddingMap.get(h.getMovieId());
            if (embedding == null) continue;

            int repeat = h.getInteractionType() == InteractionType.WATCH ? watchWeight : likeWeight;
            for (int i = 0; i < repeat; i++) {
                points.add(embedding);
            }
        }
        return points;
    }

    /**
     * 엘보우 메서드로 최적 K를 찾아 해당 K의 K-Means 결과를 반환한다.
     * K=1 ~ K_max 각각에 대해 K-Means를 실행하고 WCSS 감소폭을 비교한다.
     * 감소폭이 이전 단계의 30% 미만이 되는 시점을 최적 K로 선택한다.
     */
    private KMeansResult findOptimalResult(List<float[]> dataPoints) {
        int maxK = Math.min(kMax, dataPoints.size());

        KMeansResult[] results = new KMeansResult[maxK + 1];
        double[] wcss = new double[maxK + 1];

        for (int k = 1; k <= maxK; k++) {
            results[k] = runKMeans(dataPoints, k);
            wcss[k] = computeWCSS(dataPoints, results[k]);
        }

        int optimalK = maxK;
        for (int k = 2; k < maxK; k++) {
            double prevDrop = wcss[k - 1] - wcss[k];
            double currDrop = wcss[k] - wcss[k + 1];
            if (prevDrop > 0 && currDrop < ELBOW_THRESHOLD * prevDrop) {
                optimalK = k;
                break;
            }
        }

        return results[optimalK];
    }

    /**
     * K-Means++ 초기화 후 표준 K-Means를 실행한다.
     * 중심점 이동이 CONVERGENCE_TOLERANCE 이하이거나 MAX_ITERATIONS에 도달하면 종료한다.
     */
    private KMeansResult runKMeans(List<float[]> points, int k) {
        int dim = points.get(0).length;
        float[][] centroids = initializeCentroids(points, k);
        int[] assignments = new int[points.size()];

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            boolean changed = false;
            for (int i = 0; i < points.size(); i++) {
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double dist = euclideanDistanceSq(points.get(i), centroids[j]);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = j;
                    }
                }
                if (assignments[i] != best) {
                    assignments[i] = best;
                    changed = true;
                }
            }

            if (!changed) break;

            float[][] newCentroids = new float[k][dim];
            int[] counts = new int[k];
            for (int i = 0; i < points.size(); i++) {
                int c = assignments[i];
                counts[c]++;
                for (int d = 0; d < dim; d++) {
                    newCentroids[c][d] += points.get(i)[d];
                }
            }

            boolean converged = true;
            for (int j = 0; j < k; j++) {
                if (counts[j] == 0) continue;
                for (int d = 0; d < dim; d++) {
                    newCentroids[j][d] /= counts[j];
                }
                if (euclideanDistanceSq(centroids[j], newCentroids[j]) > CONVERGENCE_TOLERANCE) {
                    converged = false;
                }
                centroids[j] = newCentroids[j];
            }

            if (converged) break;
        }

        return new KMeansResult(assignments, centroids);
    }

    /**
     * K-Means++ 방식으로 초기 중심점을 선택한다.
     * 이미 선택된 중심점들과의 최소 거리² 비례 확률로 다음 중심점을 선택해
     * 초기 중심점 분산을 높이고 수렴 속도를 개선한다.
     */
    private float[][] initializeCentroids(List<float[]> points, int k) {
        Random random = new Random();
        float[][] centroids = new float[k][];
        centroids[0] = points.get(random.nextInt(points.size()));

        for (int i = 1; i < k; i++) {
            double[] distances = new double[points.size()];
            double total = 0;
            for (int j = 0; j < points.size(); j++) {
                double minDist = Double.MAX_VALUE;
                for (int c = 0; c < i; c++) {
                    minDist = Math.min(minDist, euclideanDistanceSq(points.get(j), centroids[c]));
                }
                distances[j] = minDist;
                total += minDist;
            }

            double r = random.nextDouble() * total;
            double cumSum = 0;
            centroids[i] = points.get(points.size() - 1);
            for (int j = 0; j < points.size(); j++) {
                cumSum += distances[j];
                if (cumSum >= r) {
                    centroids[i] = points.get(j);
                    break;
                }
            }
        }

        return centroids;
    }

    private double computeWCSS(List<float[]> points, KMeansResult result) {
        double wcss = 0;
        for (int i = 0; i < points.size(); i++) {
            wcss += euclideanDistanceSq(points.get(i), result.centroids()[result.assignments()[i]]);
        }
        return wcss;
    }

    private double euclideanDistanceSq(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * K-Means 결과를 ClusterCenter 리스트로 변환한다.
     * 포인트가 없는 빈 클러스터는 제외한다.
     * weight = 클러스터 내 포인트 수 / 전체 포인트 수 (가중치 복제 포함)
     */
    private List<ClusterCenter> buildClusterCenters(KMeansResult result, int totalPoints) {
        float[][] centroids = result.centroids();
        int[] assignments = result.assignments();
        int k = centroids.length;

        int[] counts = new int[k];
        for (int assignment : assignments) {
            counts[assignment]++;
        }

        List<ClusterCenter> centers = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            if (counts[j] == 0) continue;
            centers.add(new ClusterCenter(centroids[j], (double) counts[j] / totalPoints));
        }
        return centers;
    }

    private record KMeansResult(int[] assignments, float[][] centroids) {}
}
