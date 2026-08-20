package com.chatspace.api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * クライアントIPの取得と、レート制限キーの組み立て(フェーズ12)。
 *
 * <p><b>{@code X-Forwarded-For}を自前で解釈しない</b>(レビュー指摘対応): 以前はこのヘッダの先頭値を
 * 無検証で採用していたため、<b>クライアントが値を変えるだけでレート制限を回避できた</b>(リクエストごとに 別のIPを名乗れば毎回新しいキーになる)。現在は{@code
 * HttpServletRequest#getRemoteAddr()}のみを使い、 プロキシヘッダの解釈は{@code
 * server.forward-headers-strategy}(既定{@code native} = Tomcatの {@code
 * RemoteIpValve})に委ねる。同バルブは<b>信頼済みプロキシ(既定で私有アドレス範囲)からの リクエストに限って</b>{@code
 * X-Forwarded-For}を反映するため、外部から直接送られた偽装ヘッダは無視される。
 *
 * <p>ローカル開発やテストのようにプロキシを介さない構成では、そのままソケットの接続元アドレスが返る。
 */
public final class ClientAddress {

  private static final String UNKNOWN = "unknown";

  private ClientAddress() {}

  /**
   * クライアントIPを取得する。取得できない場合は{@code "unknown"}を返す(nullをキーに混ぜないため)。
   *
   * <p>信頼済みプロキシ配下では{@code RemoteIpValve}が{@code X-Forwarded-For}を反映済みの値を返す。
   */
  public static String of(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr == null || remoteAddr.isBlank() ? UNKNOWN : remoteAddr;
  }

  /**
   * ログイン試行のレート制限キー。ユーザーIDとIPの両方を含める理由は{@link AuthRateLimiter}のクラスコメントを参照。
   *
   * <p>ユーザーIDは大文字小文字の正規化を行わない。{@code UserRepository#findByUserId}が完全一致検索であり {@code Alice}と{@code
   * alice}は別アカウントとして扱われるため、正規化すると無関係なアカウント同士の カウンタが合算されてしまう。攻撃者から見ても、特定アカウントを破るには正確なユーザーIDを送る必要があり、
   * 大文字小文字を変えて上限を回避することはできない(別キーになるが、そもそも認証に成功しない)。
   */
  public static String loginKey(String attemptedUserId, String remoteAddress) {
    return "login|" + (attemptedUserId == null ? "" : attemptedUserId) + "|" + remoteAddress;
  }
}
