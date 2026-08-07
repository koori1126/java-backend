package com.example.backend.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, message);
    }
}
