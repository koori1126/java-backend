package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * リクエストごとに一意なtraceIdを発行し、
 *   - ログ出力(MDC経由。log4j2-spring.xmlのパターンに %X{traceId} を含める)
 *   - レスポンスヘッダー(X-Trace-Id)
 *   - エラーレスポンスのボディ(ErrorResponse.traceId)
 * すべてで同じ値を使えるようにする。
 *
 * これにより「問い合わせのあったエラーが、サーバーログのどの行に対応するか」を
 * traceId一つで突き合わせられるようになる。
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // スレッドプールでスレッドが使い回されるため、リクエスト終了時に必ずクリアする
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
