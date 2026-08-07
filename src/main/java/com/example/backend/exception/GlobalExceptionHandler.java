package com.example.backend.exception;

import com.example.backend.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * アプリ全体の例外を一箇所でハンドリングし、統一フォーマットのエラーレスポンスを返す。
 *
 * ログ出力方針:
 *   - BusinessException(想定内のエラー): warn レベル。スタックトレースは出さない
 *     (想定内なので、大量のノイズになるスタックトレースは不要)
 *   - それ以外(想定外のエラー): error レベルでスタックトレースまで出力する
 *
 * どちらのケースも、レスポンスのtraceIdとログのtraceId(MDC経由)が一致するため、
 * 問い合わせ時にtraceIdを教えてもらえばログを特定できる。
 *
 * 新しい例外クラスを追加した場合は、ここに @ExceptionHandler を追加する。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("[{}] {} path={}", errorCode.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse body = buildBody(errorCode.getHttpStatus(), errorCode.getCode(), ex.getMessage(), request, null);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.warn("[{}] {} path={} details={}", ErrorCode.VALIDATION_FAILED.getCode(),
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request.getRequestURI(), details);

        ErrorResponse body = buildBody(ErrorCode.VALIDATION_FAILED.getHttpStatus(),
                ErrorCode.VALIDATION_FAILED.getCode(), ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                request, details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        // DB制約違反(一意制約違反等)。並列リクエストによるすり抜けもここで最終的に捕捉される。
        log.warn("[{}] DB制約違反: {} path={}", ErrorCode.USER_EMAIL_DUPLICATE.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse body = buildBody(HttpStatus.CONFLICT, ErrorCode.USER_EMAIL_DUPLICATE.getCode(),
                "登録済みのデータと重複しています", request, null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("[{}] アップロードサイズ超過 path={}", ErrorCode.FILE_UPLOAD_ERROR.getCode(), request.getRequestURI());

        ErrorResponse body = buildBody(ErrorCode.FILE_UPLOAD_ERROR.getHttpStatus(),
                ErrorCode.FILE_UPLOAD_ERROR.getCode(), "アップロード可能なファイルサイズを超えています", request, null);
        return ResponseEntity.status(ErrorCode.FILE_UPLOAD_ERROR.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 想定外のエラーのみスタックトレースまでログに残す
        log.error("[{}] 予期しないエラー path={}", ErrorCode.INTERNAL_ERROR.getCode(), request.getRequestURI(), ex);

        ErrorResponse body = buildBody(ErrorCode.INTERNAL_ERROR.getHttpStatus(),
                ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(body);
    }

    private ErrorResponse buildBody(HttpStatus status, String errorCode, String message,
                                     HttpServletRequest request, List<String> details) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .errorCode(errorCode)
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .traceId(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY))
                .details(details)
                .build();
    }
}
