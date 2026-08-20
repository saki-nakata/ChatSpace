package com.chatspace.api.audit;

import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * ログ出力用に、現在のリクエストの認証済みユーザーIDを「例外を投げずに」取得する(フェーズ12)。
 *
 * <p>{@code CurrentUserArgumentResolver}は未認証時に例外を投げる(コントローラ引数の解決という用途上
 * それが正しい)が、ログ出力では未認証も正常な記録対象のため、{@code null}を返す口を別に用意する。 {@code
 * AnonymousAuthenticationToken}を未認証として扱う点は同リゾルバと揃えている。
 */
@Component
public class CurrentUserProvider {

  /** 認証済みならユーザーID、未認証・匿名なら{@code null}。 */
  public UUID currentUserIdOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException e) {
      // principal名がUUIDでない構成に変わった場合でも、ログ出力が例外でリクエストを壊さないようにする
      return null;
    }
  }
}
