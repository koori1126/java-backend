package com.example.backend.exception;

/**
 * 業務上想定される例外の基底クラス。
 * 「不正な状態だが、システム異常ではない」ケース(データが見つからない、
 * 重複している等)はこのクラスを継承した例外として表現する。
 *
 * ErrorCode を持たせることで、ログとレスポンスの両方で同じコードを
 * 使い回せるようにしている。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
