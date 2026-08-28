package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Okta(OIDC)によるログイン認証の設定。
 *
 * 【採用している方式】
 * BFF(フロントエンド専用の中間サーバー)は採用せず、Spring Boot自身が
 * OAuth2クライアントとしてOktaとやり取りし、セッションCookieでログイン状態を
 * 管理する方式(詳細は docs/features/auth.md を参照)。フロントエンドの
 * JavaScriptは、Oktaのトークンに一切触れない。
 *
 * 【CSRF対策について】
 * セッションCookie方式ではCSRF対策が必須となる。CookieCsrfTokenRepositoryを
 * 使い、CSRFトークンをCookie(XSRF-TOKEN)で配布する方式にしている。
 * フロントエンド側は、このCookieの値を読み取り、更新系リクエスト
 * (POST/PUT/DELETE)のヘッダー(X-XSRF-TOKEN)に載せて送り返す必要がある
 * (③のフロントエンド実装で対応する)。
 *
 * 【セッションアフィニティについて】
 * ロードバランサーのセッションアフィニティが有効な前提のため、
 * セッション情報はSpring Bootのデフォルト(サーバーのメモリ上)のまま保持する。
 * Spring Session JDBC等は導入していない(詳細はdocs/features/auth.mdの
 * 「デプロイ時にセッションが切れる問題」を参照)。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.post-logout-redirect-uri}")
    private String postLogoutRedirectUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // ロードバランサー/監視ツールからのヘルスチェックは認証不要にする
                        .requestMatchers("/actuator/health/**").permitAll()
                        // API仕様書は開発中の確認用に認証不要にしておく
                        // (本番公開前に、この行を削除するか制限を検討すること)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                );

        return http.build();
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        // Oktaの「Sign-out redirect URIs」に登録した値と完全に一致させること
        handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        return handler;
    }
}
