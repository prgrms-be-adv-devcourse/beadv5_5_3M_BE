package com.example.creatorservice.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Component
public class FfprobeVideoValidator {

    /**
     * ffprobe로 코덱명과 영상 길이(초)를 한 번에 추출한다.
     * 코덱이 h264가 아니면 예외를 던진다.
     */
    public FfprobeResult validate(String absoluteFilePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name,duration",
                    "-of", "default=nw=1:nk=1",
                    absoluteFilePath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String codecName = reader.readLine();
            String durationStr = reader.readLine();
            process.waitFor();

            if (codecName == null || durationStr == null) {
                throw new IllegalArgumentException("ffprobe 결과를 읽을 수 없습니다.");
            }

            int durationSeconds = (int) Math.ceil(Double.parseDouble(durationStr));
            FfprobeResult result = new FfprobeResult(codecName.trim(), durationSeconds);

            if (!result.isH264()) {
                throw new IllegalArgumentException("H.264 코덱만 허용됩니다. 현재 코덱: " + codecName);
            }

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FFprobe] 실행 실패: {}", absoluteFilePath, e);
            throw new IllegalStateException("영상 검증 중 오류가 발생했습니다.", e);
        }
    }
}