package com.example.creatorservice.presentation;

import com.example.creatorservice.infrastructure.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.nio.file.Path;

@Tag(name = "File", description = "정적 파일 서빙 API")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "파일 다운로드/스트리밍", description = "업로드된 이미지 또는 영상 파일을 반환합니다.")
    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String relativePath = requestUri.substring("/files/".length());

        // 경로 traversal 공격 방어
        // normalize()로 ../ 를 제거한 뒤, 결과 경로가 basePath 하위인지 검증
        // 단순 ".." 문자열 검사는 URL 인코딩(%2F 등) 우회 가능하므로 이 방식이 안전
        Path baseDir = fileStorageService.getBaseDirectory();
        Path targetPath = baseDir.resolve(relativePath).normalize();
        if (!targetPath.startsWith(baseDir)) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = targetPath;

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = determineContentType(relativePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".mp4"))  return "video/mp4";
        if (filename.endsWith(".m3u8")) return "application/x-mpegURL";
        if (filename.endsWith(".ts"))   return "video/MP2T";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".png"))  return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}