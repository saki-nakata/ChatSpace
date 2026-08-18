package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import org.junit.jupiter.api.Test;

/**
 * 認証機能定義書§9(実機ブラウザ確認で発見した実在するバグの回帰テスト)。
 *
 * <p>{@code /auth/me}は{@code permitAll}だが{@code @CurrentUser}を使うため、未ログイン状態での呼び出しが {@code
 * CurrentUserArgumentResolver}のバグにより500(Invalid UUID string: anonymousUser)になっていた。 Spring
 * Securityの{@code AnonymousAuthenticationToken}は{@code isAuthenticated()}が{@code true}を
 * 返す仕様であることが原因(存在チェックの見落とし)。修正後は401を返す。
 */
class AuthMeAuthorizationTest extends AbstractIntegrationTest {

  @Test
  void me_withoutCookie_returns401NotServerError() throws Exception {
    mockMvc
        .perform(get("/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("認証が必要です。"));
  }

  @Test
  void me_withValidCookie_returnsCurrentUser() throws Exception {
    User user = fixtures.createUser();
    mockMvc
        .perform(get("/auth/me").cookie(fixtures.authCookie(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.userId").value(user.getUserId()));
  }
}
