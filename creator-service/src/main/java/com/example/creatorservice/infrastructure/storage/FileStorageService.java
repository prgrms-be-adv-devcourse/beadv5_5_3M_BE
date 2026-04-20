package com.example.creatorservice.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;

public interface FileStorageService {
    /**
     * 파일을 저장하고 상대 경로를 반환한다.
     * 예) "movies/{UUID}/movie.mp4"
     */
    String store(MultipartFile file, String subDirectory, String filename);

    /**
     * 상대 경로로 저장된 파일을 삭제한다.
     */
    void delete(String relativePath);

    /**
     * 파일의 절대 경로를 반환한다.
     */
    String resolveAbsolutePath(String relativePath);

    /**
     * 업로드 기준 디렉토리의 절대 경로를 반환한다.
     * 경로 traversal 검증에 사용한다.
     */
    Path getBaseDirectory();
}