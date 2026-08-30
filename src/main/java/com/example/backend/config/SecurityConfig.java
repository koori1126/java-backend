package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Okta(OIDC)によるログイン認証の設定。
 *
 * 【採用している方式】
 * BFF(フロントエンド専用の中間サーバー)は採用せず、Spring Boot自身が
 * OAuth2クライアントとしてOktaとやり取りし、セッションCookieでログイン状態を
 * 管理する方式(詳細は docs/features/auth.md を参照)。フロントエンドの
 * JavaScriptは、Oktaのトークンに一切触れない。
 *
 * 【ログイン成功後・ログアウト後の戻り先について】
 * どちらも app.frontend-base-url (フロントエンドのURL) に統一している。
 * Oktaの「Sign-out redirect URIs」に登録した値と完全に一致させること。
 *
 * 【CSRF対策について】
 * セッションCookie方式ではCSRF対策が必須となる。CookieCsrfTokenRepositoryを
 * 使い、CSRFトークンをCookie(XSRF-TOKEN)で配布する方式にしている。
 * フロントエンド側は、このCookieの値を読み取り、更新系リクエスト
 * (POST/PUT/DELETE)のヘッダー(X-XSRF-TOKEN)に載せて送り返す必要がある
 * (api/client.tsで対応済み)。
 *
 * 【Spring Security 6.x特有の注意点】
 * Spring Security 6.xでは、CSRFトークンをCookieに書き出す処理が「遅延実行」
 * される仕様になっている。何もしないと、GETリクエストだけではCookieが
 * 発行されないことがあり、フロントエンド側がトークンを読めずCSRFエラーに
 * なる。そのため、CsrfTokenRequestAttributeHandlerを明示的に設定し、
 * さらに全リクエストでトークンを強制的に読み出すフィルタ
 * (csrfCookieFilter)を追加することで、確実にCookieが発行されるようにしている。
 * (Spring公式の「CSRF対策 for SPA」ガイドに沿った標準的な対応)
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

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

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
                .oauth2Login(oauth2 -> oauth2
                        // ログイン成功後、常にフロントエンドのトップページへ戻す
                        // (true = 元々アクセスしようとしていたURLではなく、必ずこちらへ飛ばす)
                        .defaultSuccessUrl(frontendBaseUrl, true)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                // CSRFフィルタより後段で、必ずトークンを読み出させる(Cookie発行を強制する)
                .addFilterAfter(csrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                );

        return http.build();
    }

    /**
     * リクエストごとにCsrfTokenを明示的に読み出す(.getToken()を呼ぶ)ことで、
     * 遅延書き込みされるCookieを、確実にレスポンスへ含めるようにするフィルタ。
     */
    private OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain filterChain) throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri(frontendBaseUrl);
        return handler;
    }
}
