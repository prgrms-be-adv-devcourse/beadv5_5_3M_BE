# ============================================================
# Stage 1: Layer Extraction
# - CI/CD에서 pre-built JAR를 build/libs/에 다운로드한 후 이미지 빌드
# - layertools로 FAT JAR를 4개 레이어 디렉토리로 분해
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS builder

WORKDIR /builder

COPY build/libs/*.jar app.jar

RUN java -Djarmode=layertools -jar app.jar extract

# ============================================================
# Stage 2: Final Image
# - creator-service 런타임은 ffmpeg 의존 (트랜스코딩 호출)
# - 레이어를 변경 빈도 오름차순으로 COPY (캐시 최적화)
# ============================================================
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /builder/dependencies/ ./
COPY --from=builder /builder/spring-boot-loader/ ./
COPY --from=builder /builder/snapshot-dependencies/ ./
COPY --from=builder /builder/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Duser.timezone=Asia/Seoul", \
  "org.springframework.boot.loader.launch.JarLauncher"]