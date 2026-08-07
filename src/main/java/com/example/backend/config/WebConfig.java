package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * フロントエンド(別オリジンで動作するSPA)からのアクセスを許可するためのCORS設定。
 *
 * 許可するオリジンは環境変数/プロパティ(app.cors.allowed-origins、カンマ区切り)で
 * 指定する。未設定(空)の場合はCORSを一切許可しない(本番でうっかり全許可に
 * ならないよう、デフォルトは安全側=不許可にしている)。
 *
 * ローカル開発時は application-local.yml で
 *   app.cors.allowed-origins: http://localhost:5173
 * のように、フロントの開発サーバーのオリジンを指定すること。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return;
        }
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
