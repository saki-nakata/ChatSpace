package com.chatspace.api.config;

import com.chatspace.api.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 認証機能定義書§6の方針をそのまま反映する。ステートレスJWT認証、CSRF無効化(Cookie認証 + {@code SameSite=Lax}
 * を主防御とする。前提はインフラ構成書参照)、{@code /auth/**}・{@code /health}・Swagger
 * UI/OpenAPI(フェーズ8、エンドポイント形状のみでデータを含まないため公開)のみ公開、それ以外は認証必須(未認証は401)。
 *
 * <p>{@code /ws}(STOMPハンドシェイク)はREST層のこの認証とは別に、{@code WebSocketAuthInterceptor}が独立して
 * Cookie/JWTを検証する専用の認証経路を持つ(リアルタイム通信機能定義書§5)。REST層の認証と二重の判定ロジックを
 * 持たせないよう、ここではpermitAllとしWebSocketAuthInterceptorに認証判断を一本化する。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/auth/**",
                        "/health",
                        "/ws/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
