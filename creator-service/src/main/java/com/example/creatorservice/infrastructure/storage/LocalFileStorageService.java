package com.example.creatorservice.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload.base-path}")
    private String basePath;

    @Override
    public String store(MultipartFile file, String subDirectory, String filename) {
        try {
            Path targetDir = Paths.get(basePath, subDirectory);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(filename);
            file.transferTo(targetPath.toFile());

            String relativePath = subDirectory + "/" + filename;
            log.info("[FileStorage] 저장 완료: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            log.error("[FileStorage] 저장 실패: {}/{}", subDirectory, filename, e);
            throw new IllegalStateException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null) return;
        try {
            Path path = Paths.get(basePath, relativePath);
            Files.deleteIfExists(path);

            // 빈 UUID 폴더 정리
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.delete(parent);
                    }
                }
            }
            log.info("[FileStorage] 삭제 완료: {}", relativePath);
        } catch (IOException e) {
            log.warn("[FileStorage] 삭제 실패 (무시): {}", relativePath, e);
        }
    }

    @Override
    public String resolveAbsolutePath(String relativePath) {
        return Paths.get(basePath, relativePath).toAbsolutePath().toString();
    }

    @Override
    public Path getBaseDirectory() {
        return Paths.get(basePath).toAbsolutePath().normalize();
    }
}