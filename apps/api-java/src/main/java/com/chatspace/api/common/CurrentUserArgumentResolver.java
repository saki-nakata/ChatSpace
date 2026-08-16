package com.chatspace.api.common;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} が付与された {@link UUID} 引数に、SecurityContext上の認証済みユーザーIDを解決して渡す。
 *
 * <p>ユーザーIDは {@code JwtAuthenticationFilter} が {@code Authentication#getName()}
 * に内部ユーザーID(UUID文字列)として セットしている(計画書§3、認証機能定義書§6のJWT claim方針と同一)。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUser.class)
        && parameter.getParameterType().equals(UUID.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    // Spring Securityの AnonymousAuthenticationToken は isAuthenticated() が true を返す仕様
    // (「匿名として認証済み」という特殊な状態のため)。これを見落とすと、/auth/me のような
    // permitAll だが @CurrentUser を使うエンドポイントで、未ログイン時に principal名("anonymousUser")を
    // UUIDとして解釈しようとして例外になる(実機ブラウザ確認で発見した実在するバグ)。
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw new AuthenticationCredentialsNotFoundException("認証が必要です。");
    }
    return UUID.fromString(authentication.getName());
  }
}
