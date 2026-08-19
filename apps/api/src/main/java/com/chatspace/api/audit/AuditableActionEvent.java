package com.chatspace.api.audit;

import java.util.UUID;

/**
 * 「成功した」ことを監査ログに残すべき操作を表すイベント(フェーズ12、レビュー指摘対応)。
 *
 * <p>Service内で直接ログを出すと、<b>メソッドから戻った後のコミットが失敗した場合でも成功ログだけが
 * 残ってしまう</b>(DB上は何も起きていないのに監査ログには実行された形跡が残る)。キックの場合は、 成功ログと{@code
 * MEMBER_KICK_ROLLED_BACK}が両方出るという矛盾した記録にもなりうる。
 *
 * <p>そのため、Serviceはこのイベントを発行するだけに留め、実際のログ出力は {@link
 * AuditableActionEventListener}が{@code @TransactionalEventListener(AFTER_COMMIT)}で行う。 キック確定({@code
 * MemberKickedEvent})と同じ設計を踏襲している。
 *
 * @param actorUserId 操作の実行者
 * @param workspaceId 対象ワークスペース
 * @param action 操作種別(例: {@code CHANNEL_CREATE})。ログ検索の絞り込みキーになる
 * @param targetResourceId 操作対象のリソースID(チャンネルID・対象ユーザーID等)
 * @param ownerOnly オーナー限定操作か。自主退出のような一般メンバーでも実行できる操作と区別する
 */
public record AuditableActionEvent(
    UUID actorUserId, UUID workspaceId, String action, UUID targetResourceId, boolean ownerOnly) {

  /** オーナー限定操作(チャンネル作成・削除、招待、キック)。 */
  public static AuditableActionEvent ownerAction(
      UUID actorUserId, UUID workspaceId, String action, UUID targetResourceId) {
    return new AuditableActionEvent(actorUserId, workspaceId, action, targetResourceId, true);
  }

  /** 一般メンバーが自分自身に対して行える操作(チャンネルからの自主退出等)。 */
  public static AuditableActionEvent memberAction(
      UUID actorUserId, UUID workspaceId, String action, UUID targetResourceId) {
    return new AuditableActionEvent(actorUserId, workspaceId, action, targetResourceId, false);
  }
}
