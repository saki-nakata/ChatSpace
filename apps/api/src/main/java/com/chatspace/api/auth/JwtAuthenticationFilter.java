package com.chatspace.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cookie({@code chatspace_token})のJWTを検証し、SecurityContextに認証情報をセットする。
 *
 * <p>Cookie欠如・不正・期限切れの場合でも401を返さず、SecurityContextを空のまま後続のフィルタチェーンへ委譲する。
 * 公開エンドポイント(/auth/**・/health)の可用性を損なわないための設計であり、認証必須判定自体はSpring
 * Securityの認可フィルタ・AuthenticationEntryPointが行う(認証機能定義書§3.5)。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String COOKIE_NAME = "chatspace_token";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    extractToken(request)
        .flatMap(jwtService::verify)
        .ifPresent(
            userId -> {
              var authentication =
                  new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
              SecurityContextHolder.getContext().setAuthentication(authentication);
            });
    filterChain.doFilter(request, response);
  }

  private Optional<String> extractToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }
}
