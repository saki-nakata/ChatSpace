package com.chatspace.api.audit;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * 監査ログの出力口(ログ運用設計書§2、フェーズ12)。
 *
 * <p><b>ロガー名を{@code AUDIT}に固定する</b>ことで、通常のアプリケーションログと混ざらず、Renderの
 * ログ検索やログ集約基盤側で監査イベントだけを抽出できるようにする(ログ運用設計書§4)。
 *
 * <p><b>出力してはならない情報</b>(ログ運用設計書§1.4): パスワード(平文・ハッシュ問わず)、JWT本体、
 * メッセージ本文・添付ファイルの内容、通知本文プレビュー、検索クエリ文字列。本クラスのメソッドは
 * いずれも<b>リソース識別子(UUID)と種別・理由コードのみ</b>を受け取る設計にしており、本文を渡せる 引数を意図的に持たせていない。呼び出し側で本文を{@code
 * reason}へ埋め込まないこと。
 *
 * <p>SLF4J 2系のfluent API({@code addKeyValue})でキー・バリューを付与する。構造化ログ出力を有効化した
 * 環境(本番)ではJSONのフィールドとして出力され、無効な環境(開発)では従来どおり人間可読な行として出る。
 */
@Component
public class AuditLogger {

  private static final Logger log = LoggerFactory.getLogger("AUDIT");

  /** ログへ出力する試行ユーザーIDの最大長。実在するユーザーIDの上限(30文字)より十分長く取る。 */
  private static final int MAX_ATTEMPTED_USER_ID_LENGTH = 64;

  /** 改行・タブを含む制御文字(ログ行の偽装に使われうる)。 */
  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

  /**
   * 監査イベント種別。ログ検索時の絞り込みキーになるため、文字列は安易に変更しないこと。
   *
   * <p>ログ運用設計書§2の「オーナー限定操作の拒否」に対応する専用イベントは設けていない。オーナー限定操作の 拒否は{@code ForbiddenException}として{@code
   * GlobalExceptionHandler}へ到達し、{@link
   * Event#AUTHORIZATION_DENIED}(HTTPメソッド・パス・例外型を含む)として一元的に記録されるため、
   * 二重に出すとログ量が増えるだけで得られる情報が変わらないと判断した。
   */
  public enum Event {
    LOGIN_FAILED,
    LOGIN_RATE_LIMITED,
    SIGNUP_RATE_LIMITED,
    AUTHORIZATION_DENIED,
    OWNER_ACTION_SUCCEEDED,
    MEMBER_ACTION_SUCCEEDED,
    MEMBER_KICKED,
    MEMBER_KICK_ROLLED_BACK,
    NOTIFICATION_DELIVERY_FAILED,
    STOMP_HANDSHAKE_REJECTED,
    STOMP_DESTINATION_DENIED,
    ATTACHMENT_ACCESS_DENIED
  }

  /**
   * ログイン失敗(ログ運用設計書§2「認証失敗」)。ユーザー不存在とパスワード不一致は区別しない (区別するとログ経由でアカウントの存在有無が漏れる)。
   *
   * @param attemptedUserId ログイン試行に使われたユーザーID文字列。パスワードは決して渡さないこと。 未認証の攻撃者が自由に決められる値のため、必ず{@link
   *     #sanitizeAttemptedUserId}を通して出力する
   */
  public void loginFailed(String attemptedUserId, String remoteAddress) {
    event(Event.LOGIN_FAILED)
        .addKeyValue("attemptedUserId", sanitizeAttemptedUserId(attemptedUserId))
        .addKeyValue("remoteAddress", remoteAddress)
        .log("ログインに失敗しました。");
  }

  /** レート制限により拒否されたログイン試行。 */
  public void loginRateLimited(String attemptedUserId, String remoteAddress) {
    event(Event.LOGIN_RATE_LIMITED)
        .addKeyValue("attemptedUserId", sanitizeAttemptedUserId(attemptedUserId))
        .addKeyValue("remoteAddress", remoteAddress)
        .log("レート制限中のログイン試行を拒否しました。");
  }

  /**
   * レート制限により拒否された新規登録試行(公開デモ化に伴う対応)。
   *
   * <p>ログインと同じイベント種別にまとめない。監視側で「総当たりログインが来ている」のか 「自動アカウント生成が来ている」のかは対応が異なるため、区別できる必要がある。
   */
  public void signupRateLimited(String attemptedUserId, String remoteAddress) {
    event(Event.SIGNUP_RATE_LIMITED)
        .addKeyValue("attemptedUserId", sanitizeAttemptedUserId(attemptedUserId))
        .addKeyValue("remoteAddress", remoteAddress)
        .log("レート制限中の新規登録試行を拒否しました。");
  }

  /**
   * ログイン試行のユーザーIDを、ログへ出力して安全な形に整える(レビュー指摘対応)。
   *
   * <p>ログ運用設計書§2は認証失敗の記録項目として{@code attemptedUserId}を明示的に許可しているが、
   * この値は<b>未認証の攻撃者が完全に制御できる入力</b>である。無加工で出力すると、(a)改行や制御文字による
   * ログ行の偽装(ログインジェクション)、(b)巨大文字列によるログ肥大、(c)攻撃者が第三者のPII(メールアドレス等)を
   * ユーザーID欄に入力することによる監査ログへのPII混入、が起こりうる。
   *
   * <p>そのため制御文字を除去し、{@link #MAX_ATTEMPTED_USER_ID_LENGTH}を超える分は切り詰めて、切り詰めた旨を
   * 明示する。調査に必要な「どのアカウントが狙われているか」の識別性は保たれる。
   */
  static String sanitizeAttemptedUserId(String attemptedUserId) {
    if (attemptedUserId == null) {
      return "";
    }
    String withoutControlChars = CONTROL_CHARACTERS.matcher(attemptedUserId).replaceAll("");
    if (withoutControlChars.length() <= MAX_ATTEMPTED_USER_ID_LENGTH) {
      return withoutControlChars;
    }
    return withoutControlChars.substring(0, MAX_ATTEMPTED_USER_ID_LENGTH) + "...(truncated)";
  }

  /**
   * 認可拒否(ログ運用設計書§1.3の403/404)。{@code errorType}には例外クラス名のみを渡し、例外メッセージは 渡さないこと(メッセージにリソース名等が含まれうるため)。
   */
  public void authorizationDenied(UUID userId, String method, String path, String errorType) {
    event(Event.AUTHORIZATION_DENIED)
        .addKeyValue("userId", userId)
        .addKeyValue("method", method)
        .addKeyValue("path", path)
        .addKeyValue("errorType", errorType)
        .log("認可により拒否しました。");
  }

  /** オーナー限定操作の成功(ログ運用設計書§2)。 */
  public void ownerActionSucceeded(
      UUID actorUserId, UUID workspaceId, String action, UUID targetResourceId) {
    successEvent(Event.OWNER_ACTION_SUCCEEDED)
        .addKeyValue("actorUserId", actorUserId)
        .addKeyValue("workspaceId", workspaceId)
        .addKeyValue("action", action)
        .addKeyValue("targetResourceId", targetResourceId)
        .log("オーナー限定操作を実行しました。");
  }

  /**
   * 一般メンバーが自分自身に対して行える操作の成功(チャンネルからの自主退出等)。
   *
   * <p>オーナーによる強制退出({@link Event#OWNER_ACTION_SUCCEEDED}の{@code CHANNEL_KICK})と
   * <b>必ず区別する</b>(レビュー指摘対応)。両者を同じイベントで記録すると、監査時に「オーナーが誰かを 追い出した」のか「本人が自分で抜けた」のかが判別できなくなる。
   */
  public void memberActionSucceeded(
      UUID actorUserId, UUID workspaceId, String action, UUID targetResourceId) {
    successEvent(Event.MEMBER_ACTION_SUCCEEDED)
        .addKeyValue("actorUserId", actorUserId)
        .addKeyValue("workspaceId", workspaceId)
        .addKeyValue("action", action)
        .addKeyValue("targetResourceId", targetResourceId)
        .log("メンバー操作を実行しました。");
  }

  /**
   * キック確定(AFTER_COMMIT)と、それに伴う強制切断(ログ運用設計書§2)。
   *
   * @param closedSessionCount 強制切断したWebSocketセッション数。0件でもキック自体は成立する
   */
  public void memberKicked(UUID targetUserId, int closedSessionCount) {
    successEvent(Event.MEMBER_KICKED)
        .addKeyValue("targetUserId", targetUserId)
        .addKeyValue("closedSessionCount", closedSessionCount)
        .log("キックが確定し、対象ユーザーのWebSocketセッションを切断しました。");
  }

  /**
   * キック処理のロールバック(ログ運用設計書§2)。強制切断が実施されなかったことを確認できるよう、 キック成功({@link
   * Event#MEMBER_KICKED})とは明確に別イベントとして記録する。
   */
  public void memberKickRolledBack(UUID targetUserId) {
    event(Event.MEMBER_KICK_ROLLED_BACK)
        .addKeyValue("targetUserId", targetUserId)
        .log("キック処理がロールバックされたため、強制切断は実施していません。");
  }

  /** 通知配信の失敗(ログ運用設計書§2)。通知本文は渡さないこと。 */
  public void notificationDeliveryFailed(
      UUID targetUserId, String notificationType, String reason) {
    event(Event.NOTIFICATION_DELIVERY_FAILED)
        .addKeyValue("targetUserId", targetUserId)
        .addKeyValue("notificationType", notificationType)
        .addKeyValue("reason", reason)
        .log("通知の配信に失敗しました。");
  }

  /** STOMPハンドシェイクの拒否(許可外Origin・JWT検証失敗)。 */
  public void stompHandshakeRejected(String remoteAddress, String origin, String reason) {
    event(Event.STOMP_HANDSHAKE_REJECTED)
        .addKeyValue("remoteAddress", remoteAddress)
        .addKeyValue("origin", origin)
        .addKeyValue("reason", reason)
        .log("STOMPハンドシェイクを拒否しました。");
  }

  /** SUBSCRIBE/SENDのdefault-denyによる拒否。宛先はリソース識別子のみを含むため記録してよい。 */
  public void stompDestinationDenied(
      UUID userId, String command, String destination, String reason) {
    event(Event.STOMP_DESTINATION_DENIED)
        .addKeyValue("userId", userId)
        .addKeyValue("command", command)
        .addKeyValue("destination", destination)
        .addKeyValue("reason", reason)
        .log("STOMP宛先へのアクセスを拒否しました。");
  }

  /** 添付ファイルのライブ権限再チェックによる拒否(AUTH-N07に対応する運用側の記録)。 */
  public void attachmentAccessDenied(UUID userId, String storageKey, String reason) {
    event(Event.ATTACHMENT_ACCESS_DENIED)
        .addKeyValue("userId", userId)
        .addKeyValue("storageKey", storageKey)
        .addKeyValue("reason", reason)
        .log("添付ファイルへのアクセスを拒否しました。");
  }

  /** 拒否・失敗イベント用(ログ運用設計書§1.2: 認可拒否・認証失敗はWARN以上で必ず記録する)。 */
  private LoggingEventBuilder event(Event event) {
    return log.atWarn().addKeyValue("auditEvent", event.name());
  }

  /**
   * 正常に完了した操作の記録用(ログ運用設計書§1.2: オーナー限定操作の実行結果・キック確定イベントはINFO)。
   * 拒否・失敗と同じWARNにすると、監視側で「WARNの増加=異常」という単純な閾値が使えなくなるため分ける。
   */
  private LoggingEventBuilder successEvent(Event event) {
    return log.atInfo().addKeyValue("auditEvent", event.name());
  }
}
