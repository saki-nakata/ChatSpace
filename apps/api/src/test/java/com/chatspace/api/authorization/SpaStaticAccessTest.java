package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

/**
 * フェーズ14(Render同梱配信化)でSecurityConfigに追加したSPAシェル用permitAllの回帰テスト。
 *
 * <p>フロントエンドをSpring Bootへ同梱配信する前は{@code apps/web}が別プロセス配信でSpring Securityの対象外
 * だったため問題化しなかったが、同梱配信に切り替えると未認証ユーザーが{@code /login}等にアクセスしただけで401になり SPA自体が読み込めなくなる(致命的バグ)。実際の{@code
 * index.html}はDockerビルド後にのみ静的リソースとして
 * 存在するため、ここでは401にならないことのみを検証する(コンテンツの検証はDocker実機確認で行う、実装計画書/phase14.md参照)。
 */
class SpaStaticAccessTest extends AbstractIntegrationTest {

  @Test
  void login_withoutCookie_isNotUnauthorized() throws Exception {
    assertNotUnauthorized(get("/login"));
  }

  @Test
  void signup_withoutCookie_isNotUnauthorized() throws Exception {
    assertNotUnauthorized(get("/signup"));
  }

  @Test
  void workspaceRoute_withoutCookie_isNotUnauthorized() throws Exception {
    assertNotUnauthorized(get("/w/{workspaceId}", UUID.randomUUID()));
  }

  /** 既存の認可境界への回帰が無いことの確認: 実際のAPIは引き続き認証必須のまま。 */
  @Test
  void workspacesApi_withoutCookie_returns401() throws Exception {
    mockMvc.perform(get("/workspaces")).andExpect(status().isUnauthorized());
  }

  @Test
  void uploadsApi_withoutCookie_returns401() throws Exception {
    mockMvc
        .perform(get("/uploads/{storageKey}", "abc123.png"))
        .andExpect(status().isUnauthorized());
  }

  private void assertNotUnauthorized(org.springframework.test.web.servlet.RequestBuilder request)
      throws Exception {
    MvcResult result = mockMvc.perform(request).andReturn();
    assertNotEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus());
  }
}
