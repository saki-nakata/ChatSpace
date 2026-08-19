package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ログインのレート制限をHTTP経由で検証する(認証機能定義書§7、フェーズ12)。
 *
 * <p>ウィンドウ・ブロック時間の経過そのものは{@code AuthRateLimiterTest}(仮想時計を使う単体テスト)で
 * 担保しているため、ここでは「規定回数を超えると429とRetry-Afterが返る」「成功でカウンタが解除される」 というエンドポイント側の結線を確認する。
 */
class LoginRateLimitIntegrationTest extends AbstractIntegrationTest {

  /** {@code application-test.yml}の{@code chatspace.auth-rate-limit.max-attempts}と一致させること。 */
  private static final int MAX_ATTEMPTS = 5;

  private static final String PASSWORD = "correct-horse-battery-staple";

  /** 規定回数を超えた連続失敗が429になり、Retry-Afterヘッダが付くこと。 */
  @Test
  void exceedingMaxAttempts_returns429WithRetryAfter() throws Exception {
    User user = fixtures.createUserWithPassword(PASSWORD);
    String clientIp = "198.51.100.21";

    // 上限回数までは通常の認証失敗レスポンスのまま(本アプリはユーザーID列挙防止のため400で統一している)
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(loginRequest(user.getUserId(), "wrong-password", clientIp))
          .andExpect(status().isBadRequest());
    }

    // 上限到達後は照合自体を行わず429
    mockMvc
        .perform(loginRequest(user.getUserId(), "wrong-password", clientIp))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  /** ブロック中は、正しいパスワードであっても429で拒否されること(照合を実行させない)。 */
  @Test
  void whileBlocked_evenCorrectPasswordIsRejected() throws Exception {
    User user = fixtures.createUserWithPassword(PASSWORD);
    String clientIp = "198.51.100.22";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(loginRequest(user.getUserId(), "wrong-password", clientIp))
          .andExpect(status().isBadRequest());
    }

    mockMvc
        .perform(loginRequest(user.getUserId(), PASSWORD, clientIp))
        .andExpect(status().isTooManyRequests());
  }

  /** ログイン成功でカウンタが解除され、その後に再び上限回数の猶予があること。 */
  @Test
  void successfulLogin_resetsFailureCounter() throws Exception {
    User user = fixtures.createUserWithPassword(PASSWORD);
    String clientIp = "198.51.100.23";

    // 上限手前まで失敗させてから成功させる
    for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
      mockMvc
          .perform(loginRequest(user.getUserId(), "wrong-password", clientIp))
          .andExpect(status().isBadRequest());
    }
    mockMvc.perform(loginRequest(user.getUserId(), PASSWORD, clientIp)).andExpect(status().isOk());

    // カウンタが解除されているため、再び上限手前まで通常の認証失敗として試せる(429にならない)
    for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
      mockMvc
          .perform(loginRequest(user.getUserId(), "wrong-password", clientIp))
          .andExpect(status().isBadRequest());
    }
  }

  /** 別のクライアントIPからの試行が巻き込まれないこと(同一NAT配下の利用者を締め出さない)。 */
  @Test
  void blockingOneClient_doesNotAffectAnotherAddress() throws Exception {
    User user = fixtures.createUserWithPassword(PASSWORD);
    String blockedIp = "198.51.100.24";
    String otherIp = "203.0.113.24";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(loginRequest(user.getUserId(), "wrong-password", blockedIp))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(loginRequest(user.getUserId(), "wrong-password", blockedIp))
        .andExpect(status().isTooManyRequests());

    // 別IPからは通常どおり認証が実行される(正しいパスワードなら成功する)
    mockMvc.perform(loginRequest(user.getUserId(), PASSWORD, otherIp)).andExpect(status().isOk());
  }

  /**
   * 存在しないユーザーIDへの総当たりもブロックされること。
   *
   * <p>「ユーザー不存在」もパスワード不一致と同一レスポンスで扱う(§3.2のユーザーID列挙防止)ため、 レート制限も同様に効く必要がある。
   * ここが抜けていると、攻撃者はユーザーID列挙を無制限に試せてしまう。
   */
  @Test
  void unknownUserId_isAlsoRateLimited() throws Exception {
    String clientIp = "198.51.100.25";
    String unknownUserId = "no-such-user-" + java.util.UUID.randomUUID();

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(loginRequest(unknownUserId, "wrong-password", clientIp))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(loginRequest(unknownUserId, "wrong-password", clientIp))
        .andExpect(status().isTooManyRequests());
  }

  /**
   * <b>{@code X-Forwarded-For}を送るだけではレート制限を回避できないこと</b>(レビュー指摘の回帰テスト)。
   *
   * <p>以前はこのヘッダの先頭値を無検証で採用していたため、リクエストごとに別のIPを名乗るだけで 毎回新しいキーになり、上限を無制限に回避できた。現在は信頼済みプロキシが解決した
   * {@code getRemoteAddr()}のみを使うため、ヘッダを偽装しても同一キーに集約される。
   */
  @Test
  void spoofedForwardedForHeader_doesNotBypassRateLimit() throws Exception {
    User user = fixtures.createUserWithPassword(PASSWORD);
    String actualIp = "198.51.100.26";

    // 毎回異なる X-Forwarded-For を名乗りながら上限まで試行する
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(
              loginRequest(user.getUserId(), "wrong-password", actualIp)
                  .header("X-Forwarded-For", "203.0.113." + i))
          .andExpect(status().isBadRequest());
    }

    mockMvc
        .perform(
            loginRequest(user.getUserId(), "wrong-password", actualIp)
                .header("X-Forwarded-For", "203.0.113.200"))
        .andExpect(status().isTooManyRequests());
  }

  /** 巨大なユーザーIDが入口で弾かれること(監査ログの肥大化・不要なbcrypt照合の防止)。 */
  @Test
  void oversizedUserId_isRejectedByValidation() throws Exception {
    mockMvc
        .perform(loginRequest("a".repeat(5_000), "wrong-password", "198.51.100.27"))
        .andExpect(status().isBadRequest());
  }

  /**
   * レート制限のカウンタは{@code @Component}のシングルトンでアプリケーション全体に共有されるため、
   * テスト間で状態が漏れないよう各テストは固有のクライアントIP・ユーザーを使う(明示的な後片付けは不要)。
   */
  private MockHttpServletRequestBuilder loginRequest(
      String userId, String password, String clientIp) throws Exception {
    return post("/auth/login")
        .with(
            request -> {
              // クライアントIPは getRemoteAddr() から取る(X-Forwarded-For の自前解釈はレビュー指摘により廃止)
              request.setRemoteAddr(clientIp);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("userId", userId, "password", password)));
  }
}
