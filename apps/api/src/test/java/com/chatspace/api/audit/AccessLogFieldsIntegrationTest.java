package com.chatspace.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * アクセスログ・監査ログに必要なフィールドが実際に載っていることを検証する(フェーズ12、レビュー指摘対応)。
 *
 * <p>レビューで、認証済みリクエストでも{@code userId}が常に{@code null}になっていることが指摘された。 原因は、{@code
 * RequestLoggingFilter}がSpring Securityのフィルタチェーンより外側で動くため、ログ出力時点では {@code
 * SecurityContext}が既にクリアされていたこと。この回帰を防ぐため、Logbackの{@link ListAppender}で 実際のログイベントを捕捉してキー・バリューを検証する。
 */
class AccessLogFieldsIntegrationTest extends AbstractIntegrationTest {

  private ListAppender<ILoggingEvent> accessAppender;
  private ListAppender<ILoggingEvent> auditAppender;

  @BeforeEach
  void attachAppenders() {
    accessAppender = attach("ACCESS");
    auditAppender = attach("AUDIT");
  }

  @AfterEach
  void detachAppenders() {
    detach("ACCESS", accessAppender);
    detach("AUDIT", auditAppender);
  }

  /** 認証済みリクエストのアクセスログに、実際の{@code userId}が載ること。 */
  @Test
  void accessLogIncludesUserIdForAuthenticatedRequest() throws Exception {
    User user = fixtures.createUser();

    mockMvc.perform(get("/auth/me").cookie(fixtures.authCookie(user))).andExpect(status().isOk());

    ILoggingEvent event = lastEventFor(accessAppender, "/auth/me");
    Map<String, Object> fields = keyValues(event);
    assertEquals(
        user.getId().toString(),
        String.valueOf(fields.get("userId")),
        "認証済みリクエストのアクセスログには実際のユーザーIDが載るはず(nullになっていた回帰の防止)");
    assertEquals("GET", fields.get("method"));
    assertEquals("/auth/me", fields.get("path"));
    assertEquals(200, fields.get("status"));
    assertNotNull(fields.get("durationMs"));
  }

  /** 未認証リクエストでは{@code userId}が{@code null}のまま記録されること(過剰な埋め込みをしない)。 */
  @Test
  void accessLogHasNullUserIdForUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

    ILoggingEvent event = lastEventFor(accessAppender, "/auth/me");
    Map<String, Object> fields = keyValues(event);
    assertNull(fields.get("userId"), "未認証リクエストではuserIdはnullのままであるはず");
    assertEquals(401, fields.get("status"));
  }

  /** 監査ログ(認可拒否)にイベント種別・パス・例外型が載ること。 */
  @Test
  void auditLogIncludesEventFieldsOnAuthorizationDenial() throws Exception {
    User outsider = fixtures.createUser();
    // 存在しないワークスペースへのアクセスは404(認可による非可視化と同じ経路)
    mockMvc
        .perform(
            get("/workspaces/{id}/channels", java.util.UUID.randomUUID())
                .cookie(fixtures.authCookie(outsider)))
        .andExpect(status().isNotFound());

    ILoggingEvent event =
        auditAppender.list.stream()
            .filter(e -> "AUTHORIZATION_DENIED".equals(keyValues(e).get("auditEvent")))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("AUTHORIZATION_DENIED の監査ログが出ていない"));

    Map<String, Object> fields = keyValues(event);
    assertEquals(outsider.getId().toString(), String.valueOf(fields.get("userId")));
    assertEquals("GET", fields.get("method"));
    assertNotNull(fields.get("path"));
    assertNotNull(fields.get("errorType"));
    assertEquals(Level.WARN, event.getLevel(), "認可拒否はWARN以上で記録するはず(ログ運用設計書§1.2)");
  }

  /** 攻撃者が制御できるユーザーIDが、制御文字を除去し切り詰めた形で記録されること。 */
  @Test
  void loginFailureAuditSanitizesAttemptedUserId() {
    String malicious = "victim\nAUDIT : 偽装された行\r\t" + "x".repeat(500);

    String sanitized = AuditLogger.sanitizeAttemptedUserId(malicious);

    assertTrue(sanitized.indexOf('\n') < 0, "改行が残っていてはならない(ログ行の偽装を防ぐ)");
    assertTrue(sanitized.indexOf('\r') < 0, "復帰文字が残っていてはならない");
    assertTrue(sanitized.indexOf('\t') < 0, "タブが残っていてはならない");
    assertTrue(sanitized.length() <= 80, "上限を超える長さは切り詰められるはず(実際: " + sanitized.length() + ")");
    assertTrue(sanitized.endsWith("...(truncated)"), "切り詰めた事実が分かる形になっているはず");
  }

  private static ListAppender<ILoggingEvent> attach(String loggerName) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(loggerName)).addAppender(appender);
    return appender;
  }

  private static void detach(String loggerName, ListAppender<ILoggingEvent> appender) {
    ((Logger) LoggerFactory.getLogger(loggerName)).detachAppender(appender);
    appender.stop();
  }

  /** 指定パスに対する最後のログイベント。テスト間で他のリクエストが混ざっても取り違えないようにする。 */
  private static ILoggingEvent lastEventFor(
      ListAppender<ILoggingEvent> appender, String expectedPath) {
    Optional<ILoggingEvent> event =
        appender.list.stream()
            .filter(e -> expectedPath.equals(keyValues(e).get("path")))
            .reduce((first, second) -> second);
    return event.orElseThrow(() -> new AssertionError(expectedPath + " のログが出ていない"));
  }

  /** SLF4Jのfluent APIで付与されたキー・バリューをMapへ。値がnullのキーも保持する。 */
  private static Map<String, Object> keyValues(ILoggingEvent event) {
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs == null) {
      return Map.of();
    }
    Map<String, Object> fields = new HashMap<>();
    pairs.forEach(pair -> fields.put(pair.key, pair.value));
    return fields;
  }
}
