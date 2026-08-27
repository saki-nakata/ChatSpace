package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 新規登録のレート制限をHTTP経由で検証する(認証機能定義書§7)。
 *
 * <p>公開URLで誰でも登録できる構成にしたことで、自動化されたアカウント大量生成とそれに続く 添付アップロード濫用が成立しうるようになったため追加した(レビュー指摘対応)。
 *
 * <p>ウィンドウ・ブロック時間の経過そのものは{@code AuthRateLimiterTest}(仮想時計)で担保しているため、
 * ここではエンドポイント側の結線と、<b>ログインとは異なるキー体系になっていること</b>を確認する。
 */
class SignupRateLimitIntegrationTest extends AbstractIntegrationTest {

  /** {@code application-test.yml}の{@code chatspace.signup-rate-limit.max-attempts}と一致させること。 */
  private static final int MAX_ATTEMPTS = 3;

  private static final String PASSWORD = "correct-horse-battery-staple";

  /** 規定回数を超えた登録が429になり、Retry-Afterヘッダが付くこと。 */
  @Test
  void exceedingMaxAttempts_returns429WithRetryAfter() throws Exception {
    String clientIp = "198.51.100.41";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc.perform(signupRequest(uniqueUserId(), clientIp)).andExpect(status().isCreated());
    }

    mockMvc
        .perform(signupRequest(uniqueUserId(), clientIp))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  /**
   * <b>ユーザーIDを毎回変えても回避できないこと</b>(この対策の要点となる回帰テスト)。
   *
   * <p>ログインと同じ「ユーザーID + IP」キーを流用すると、新規登録では攻撃者がユーザーIDを毎回自由に 決められるため毎回別キーになり、制限が一切効かない。{@code
   * ClientAddress#signupKey}がIP単独である ことをここで担保する。上の{@link
   * #exceedingMaxAttempts_returns429WithRetryAfter()}も毎回別の ユーザーIDを使っているため、両テストが揃って初めてこの性質を示す。
   */
  @Test
  void varyingUserId_doesNotBypassRateLimit() throws Exception {
    String clientIp = "198.51.100.42";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc.perform(signupRequest(uniqueUserId(), clientIp)).andExpect(status().isCreated());
    }

    // 一度も使っていないユーザーIDでも、同一IPである限り拒否される
    mockMvc
        .perform(signupRequest(uniqueUserId(), clientIp))
        .andExpect(status().isTooManyRequests());
  }

  /** 別のクライアントIPからの登録が巻き込まれないこと(同一NAT配下ではないIPを締め出さない)。 */
  @Test
  void blockingOneClient_doesNotAffectAnotherAddress() throws Exception {
    String blockedIp = "198.51.100.43";
    String otherIp = "203.0.113.43";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc.perform(signupRequest(uniqueUserId(), blockedIp)).andExpect(status().isCreated());
    }
    mockMvc
        .perform(signupRequest(uniqueUserId(), blockedIp))
        .andExpect(status().isTooManyRequests());

    mockMvc.perform(signupRequest(uniqueUserId(), otherIp)).andExpect(status().isCreated());
  }

  /**
   * {@code X-Forwarded-For}を送るだけでは回避できないこと。
   *
   * <p>ログイン側と同じく、信頼済みプロキシが解決した{@code getRemoteAddr()}のみを使うため、 ヘッダを偽装しても同一キーに集約される。
   */
  @Test
  void spoofedForwardedForHeader_doesNotBypassRateLimit() throws Exception {
    String actualIp = "198.51.100.44";

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(
              signupRequest(uniqueUserId(), actualIp).header("X-Forwarded-For", "203.0.113." + i))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(signupRequest(uniqueUserId(), actualIp).header("X-Forwarded-For", "203.0.113.200"))
        .andExpect(status().isTooManyRequests());
  }

  /**
   * 登録に失敗した試行も枠を消費すること(ユーザーID重複などで失敗させ続けても上限に達する)。
   *
   * <p>試行枠の確保は登録処理の<b>前</b>に行うため、失敗しても消費される。ここが「成功時のみ加算」だと、
   * 失敗を繰り返すだけの負荷(bcryptハッシュ計算を含む)を無制限に掛けられてしまう。
   */
  @Test
  void failedSignupAttempts_alsoConsumeQuota() throws Exception {
    String clientIp = "198.51.100.45";
    String duplicatedUserId = uniqueUserId();

    mockMvc.perform(signupRequest(duplicatedUserId, clientIp)).andExpect(status().isCreated());

    // 2回目以降は同じユーザーIDのため登録自体は失敗するが、枠は消費される
    for (int i = 1; i < MAX_ATTEMPTS; i++) {
      mockMvc
          .perform(signupRequest(duplicatedUserId, clientIp))
          .andExpect(status().is4xxClientError());
    }

    mockMvc
        .perform(signupRequest(uniqueUserId(), clientIp))
        .andExpect(status().isTooManyRequests());
  }

  private static String uniqueUserId() {
    // ユーザーIDの長さ上限(30文字)に収まるように短縮する
    return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
  }

  /**
   * レート制限のカウンタは{@code @Component}のシングルトンでアプリケーション全体に共有されるため、
   * テスト間で状態が漏れないよう各テストは固有のクライアントIPを使う(明示的な後片付けは不要)。
   */
  private MockHttpServletRequestBuilder signupRequest(String userId, String clientIp)
      throws Exception {
    return post("/auth/signup")
        .with(
            request -> {
              request.setRemoteAddr(clientIp);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            objectMapper.writeValueAsString(
                Map.of("userId", userId, "password", PASSWORD, "displayName", userId)));
  }
}
