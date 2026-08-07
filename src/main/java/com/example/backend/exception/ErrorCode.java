package com.example.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * アプリ全体で発生しうるエラーを一元管理するコード。
 *
 * 【命名規則】
 * <ドメイン3文字>-<3桁連番>
 *   USR : ユーザー関連
 *   SYS : システム共通(想定外エラー等)
 *   VAL : 入力値検証エラー
 *
 * ログにもレスポンスにも同じコードを出力することで、
 * 「ログのこの行と、問い合わせのあったこのエラーが同じ事象か」を
 * 突き合わせやすくする狙いがある。
 *
 * 新しいエラーの種類を追加する際は、既存のコードを変更・削除せず、
 * 末尾に新しい連番を追加すること(過去のログとの整合性を保つため)。
 */
public enum ErrorCode {

    // ==== ユーザー関連 (USR) ====
    USER_NOT_FOUND("USR-001", HttpStatus.NOT_FOUND, "指定されたユーザーが見つかりません"),
    USER_EMAIL_DUPLICATE("USR-002", HttpStatus.CONFLICT, "このメールアドレスは既に登録されています"),

    // ==== 入力値検証 (VAL) ====
    VALIDATION_FAILED("VAL-001", HttpStatus.BAD_REQUEST, "入力値が不正です"),

    // ==== システム共通 (SYS) ====
    INTERNAL_ERROR("SYS-001", HttpStatus.INTERNAL_SERVER_ERROR, "予期しないエラーが発生しました"),
    FILE_UPLOAD_ERROR("SYS-002", HttpStatus.BAD_REQUEST, "ファイルの読み込みに失敗しました");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
