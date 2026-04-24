package com.example.creatorservice.infrastructure.storage;

public interface VideoProcessingService {

    /**
     * 업로드된 영상을 처리하고 DB에 저장할 상대 경로를 반환한다.
     * dev: 원본 .mp4 경로 그대로 반환
     * prod: HLS 인코딩 후 .m3u8 경로 반환
     */
    String process(String videoRelativePath);
}