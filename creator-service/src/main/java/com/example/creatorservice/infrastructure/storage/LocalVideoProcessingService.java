package com.example.creatorservice.infrastructure.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
public class LocalVideoProcessingService implements VideoProcessingService {

    @Override
    public String process(String videoRelativePath) {
        return videoRelativePath;
    }
}