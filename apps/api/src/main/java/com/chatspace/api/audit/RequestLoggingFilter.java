package com.chatspace.api.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * アクセスログとリクエストスコープのログコンテキスト(ログ運用設計書§1.5)。
 *
 * <p>{@code requestId}をMDCへ入れることで、同一リクエストから出たアプリケーションログ・監査ログを 後から突き合わせられるようにする。{@code
 * userId}は認証フィルタが{@code SecurityContext}を埋めた後で ないと分からないため、フィルタチェーンの実行後に取得してアクセスログへ載せる。
 *
 * <p><b>ログに載せない</b>(ログ運用設計書§1.4): クエリ文字列(検索クエリがユーザー入力そのものであり PIIに準ずるため{@code
 * getRequestURI()}のみを使う)、リクエスト/レスポンスボディ、Cookie値。
 *
 * <p>{@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}に近い順序で動かし、以降の全処理が {@code
 * requestId}付きでログを出せるようにする。
 */
@Component
@Order(RequestLoggingFilter.ORDER)
public class RequestLoggingFilter extends OncePerRequestFilter {

  /** Spring Securityのフィルタチェーンより手前で動かす(未認証で弾かれたリクエストも記録するため)。 */
  public static final int ORDER = -200;

  public static final String REQUEST_ID_MDC_KEY = "requestId";
  public static final String USER_ID_MDC_KEY = "userId";

  /**
   * 認証済みユーザーIDを受け渡すリクエスト属性のキー。{@code JwtAuthenticationFilter}が設定する。
   *
   * <p>本フィルタはSpring Securityのフィルタチェーンより<b>外側</b>で動くため、レスポンスを書き終えた 時点では{@code
   * SecurityContext}が既にクリアされており{@code SecurityContextHolder}からは ユーザーIDを取得できない(レビュー指摘: アクセスログの{@code
   * userId}が常にnullになっていた)。 チェーン実行中に属性へ退避しておき、ここではそれを読む。
   */
  public static final String USER_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".userId";

  private static final Logger log = LoggerFactory.getLogger("ACCESS");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    long startedAt = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
      UUID userId = authenticatedUserId(request);
      if (userId != null) {
        MDC.put(USER_ID_MDC_KEY, userId.toString());
      }
      log.atInfo()
          .addKeyValue("method", request.getMethod())
          .addKeyValue("path", request.getRequestURI())
          .addKeyValue("status", response.getStatus())
          .addKeyValue("durationMs", durationMs)
          .addKeyValue("userId", userId)
          .log("リクエストを処理しました。");
      // スレッドはプールで再利用されるため、必ず全キーを取り除く(次のリクエストへ値が漏れるのを防ぐ)
      MDC.remove(USER_ID_MDC_KEY);
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }

  /** チェーン実行中に{@code JwtAuthenticationFilter}が退避したユーザーID。未認証なら{@code null}。 */
  private static UUID authenticatedUserId(HttpServletRequest request) {
    Object attribute = request.getAttribute(USER_ID_ATTRIBUTE);
    return attribute instanceof UUID userId ? userId : null;
  }
}
