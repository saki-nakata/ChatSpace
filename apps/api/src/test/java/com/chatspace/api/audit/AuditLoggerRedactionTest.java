package com.chatspace.api.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * ログ運用設計書§1.4「ログに残さない情報」を、監査ログAPIの<b>型レベル</b>で担保する回帰テスト。
 *
 * <p>ログ出力の中身を実行時に検査するのではなく、「そもそも本文やパスワードを渡せる引数が生えていないか」を
 * 検査する。実行時検査だと呼び出し漏れのあるメソッドを見逃すが、この方式なら将来{@code AuditLogger}へ うっかり{@code String
 * body}のような引数を追加した瞬間に失敗する。
 */
class AuditLoggerRedactionTest {

  /** 引数名に含まれていてはならない語(パスワード・トークン・本文・検索クエリ等)。 */
  private static final List<String> FORBIDDEN_PARAMETER_NAMES =
      List.of(
          "password",
          "passwordhash",
          "rawpassword",
          "token",
          "jwt",
          "cookie",
          "body",
          "text",
          "content",
          "message",
          "preview",
          "query",
          "keyword",
          "email",
          "displayname");

  /**
   * {@code AuditLogger}のpublicメソッドが、本文・認証情報を受け取る引数を持たないこと。
   *
   * <p>この検査を成立させるため、ビルドは{@code -parameters}付きでコンパイルされている必要がある (未付与の場合は引数名が{@code
   * arg0}となり検査が素通りしてしまうため、その状態自体を検出して失敗させる)。
   */
  @Test
  void auditLoggerMethodsDoNotAcceptSecretsOrMessageBodies() {
    List<Method> publicMethods =
        Arrays.stream(AuditLogger.class.getDeclaredMethods())
            .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
            .filter(m -> m.getParameterCount() > 0)
            .toList();

    assertFalse(publicMethods.isEmpty(), "検査対象のメソッドが取得できていない(テスト自体の不備)");

    for (Method method : publicMethods) {
      for (Parameter parameter : method.getParameters()) {
        assertTrue(
            parameter.isNamePresent(),
            "引数名が保持されていないため検査できない。build.gradle.ktsの -parameters 設定を確認すること: " + method.getName());
        String name = parameter.getName().toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_PARAMETER_NAMES) {
          assertFalse(
              name.contains(forbidden),
              "AuditLogger#"
                  + method.getName()
                  + " の引数 '"
                  + parameter.getName()
                  + "' は、ログへ出してはならない情報(ログ運用設計書§1.4)を受け取る名前になっている。"
                  + "識別子(UUID)・種別・理由コードのみを受け取る設計を維持すること。");
        }
      }
    }
  }
}
