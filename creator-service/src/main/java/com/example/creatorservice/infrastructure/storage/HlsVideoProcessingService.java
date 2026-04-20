package com.example.creatorservice.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Profile("prod")
@Service
@RequiredArgsConstructor
public class HlsVideoProcessingService implements VideoProcessingService {

    private final FileStorageService fileStorageService;

    @Override
    public String process(String videoRelativePath) {
        String absoluteVideoPath = fileStorageService.resolveAbsolutePath(videoRelativePath);
        Path videoFile = Paths.get(absoluteVideoPath);
        Path hlsDir = videoFile.getParent();
        String m3u8AbsPath = hlsDir.resolve("index.m3u8").toString();
        String segmentPattern = hlsDir.resolve("segment_%03d.ts").toString();

        log.info("[HLS] 인코딩 시작 - input: {}", videoRelativePath);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-i", absoluteVideoPath,
                    "-c", "copy",                        // H.264 이미 검증됨 — 재인코딩 없이 컨테이너만 변환
                    "-hls_time", "10",                   // 10초 단위 세그먼트
                    "-hls_list_size", "0",               // 전체 세그먼트 목록 유지
                    "-hls_segment_filename", segmentPattern,
                    "-f", "hls",
                    m3u8AbsPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("ffmpeg HLS 인코딩 실패 (exitCode=" + exitCode + ")");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[HLS] 인코딩 중 오류 발생 - input: {}", videoRelativePath, e);
            throw new IllegalStateException("HLS 인코딩 중 오류가 발생했습니다.", e);
        }

        // 원본 .mp4 삭제 (EC2 스토리지 절약)
        fileStorageService.delete(videoRelativePath);
        log.info("[HLS] 인코딩 완료 - output: {}", videoRelativePath.replace(".mp4", "/index.m3u8"));

        // DB에 저장할 .m3u8 상대 경로 반환
        String parent = videoRelativePath.substring(0, videoRelativePath.lastIndexOf('/'));
        return parent + "/index.m3u8";
    }
}