package com.chatspace.api.config;

import com.chatspace.api.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 認証機能定義書§6の方針をそのまま反映する。ステートレスJWT認証、CSRF無効化(Cookie認証 + {@code SameSite=Lax}
 * を主防御とする。前提はインフラ構成書参照)、{@code /auth/**}・{@code /health}・Swagger
 * UI/OpenAPI(フェーズ8、エンドポイント形状のみでデータを含まないため公開)のみ公開、それ以外は認証必須(未認証は401)。
 *
 * <p>{@code /ws}(STOMPハンドシェイク)はREST層のこの認証とは別に、{@code WebSocketAuthInterceptor}が独立して
 * Cookie/JWTを検証する専用の認証経路を持つ(リアルタイム通信機能定義書§5)。REST層の認証と二重の判定ロジックを
 * 持たせないよう、ここではpermitAllとしWebSocketAuthInterceptorに認証判断を一本化する。
 *
 * <p><b>SPAシェルの配信(フェーズ14、Render同梱配信化)</b>: {@code /}・{@code /index.html}・{@code /login}・{@code
 * /signup}・{@code /assets/**}・{@code /w/**}もpermitAllに加える。これはHTML/JSシェルの配信を許可するだけで、
 * シェルが叩く実際のAPI({@code /workspaces/**}等)は引き続き認証必須のまま(未認証は401、SPA側は{@code
 * RequireAuth}コンポーネントでログイン画面へ誘導)。これが無いと未認証ユーザーが{@code /}や{@code /login}に
 * アクセスしただけで401になりSPA自体が読み込めなくなる({@code SpaFallbackController}参照)。
 *
 * <p><b>CORS(レビュー指摘対応)</b>: 従来{@code chatspace.web-origin}はWebSocketハンドシェイクの Origin検証({@code
 * WebSocketConfig}の{@code setAllowedOriginPatterns})にのみ使われ、REST側には {@code
 * CorsConfigurationSource}が存在しなかった。フロントエンド(`apps/web-next`)を別オリジンで起動した瞬間に
 * 全APIが到達不能になるため、同じプロパティを使ってREST側にも設定する。Cookie認証のため{@code
 * allowCredentials(true)}は必須(これが無いとブラウザがレスポンスを読み捨てる)。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final String webOrigin;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      @Value("${chatspace.web-origin}") String webOrigin) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.webOrigin = webOrigin;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                        "/swagger-ui.html",
                        "/",
                        "/index.html",
                        "/login",
                        "/signup",
                        "/assets/**",
                        "/w/**")
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

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of(webOrigin));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
