package com.example.userservice.exception;

public class ImageUploadFailedException extends RuntimeException {
    public ImageUploadFailedException() {
        super("이미지 업로드에 실패했습니다.");
    }
}
