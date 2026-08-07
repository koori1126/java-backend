package com.example.backend.exception;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException(String email) {
        super(ErrorCode.USER_EMAIL_DUPLICATE, "既に登録済みのメールアドレスです: " + email);
    }
}
