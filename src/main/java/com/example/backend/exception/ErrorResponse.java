package com.example.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    /** ErrorCode の code (例: "USR-001")。ログと突き合わせる際のキーになる */
    private String errorCode;
    private String error;
    private String message;
    private String path;
    /** リクエスト単位で採番されるID。ログのtraceIdと同じ値なので、
     *  問い合わせ時にこれを教えてもらえばログを特定できる */
    private String traceId;
    private List<String> details;
}
