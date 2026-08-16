package com.chatspace.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 通知機能定義書§6.1(通知のスコープ漏洩防止、最重要)に対応する。
 *
 * <p>{@link #VISIBLE_SCOPE_CONDITION} は、通知が指す channelId/dmId/workspaceId に対する現在のライブなメンバーシップを {@code
 * AND}結合で再検証する条件式であり、一覧取得・未読件数・個別既読・全件既読の全メソッドで共通利用する。 DM側のEXISTSは{@code
 * workspace_members}とのJOINを必ず含める({@code DmThread}の参加者情報はワークスペースキック後も
 * 消えないため、参加者チェックのみでは不十分)。3条件を{@code OR}結合すると、チャンネル通知が{@code dm_id IS NULL}分岐で
 * 常に真になりフィルタが無効化される致命的バグになるため、必ず{@code AND}で結合すること。
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  String VISIBLE_SCOPE_CONDITION =
      "(n.channelId IS NULL OR EXISTS (SELECT 1 FROM ChannelMember cm WHERE cm.channelId ="
          + " n.channelId AND cm.userId = :userId)) AND (n.dmId IS NULL OR EXISTS (SELECT 1 FROM"
          + " DmThread dt, WorkspaceMember wm WHERE wm.workspaceId = dt.workspaceId AND wm.userId"
          + " = :userId AND dt.id = n.dmId AND (dt.userAId = :userId OR dt.userBId = :userId)))"
          + " AND (n.workspaceId IS NULL OR EXISTS (SELECT 1 FROM WorkspaceMember wm2 WHERE"
          + " wm2.workspaceId = n.workspaceId AND wm2.userId = :userId))";

  @Query(
      "SELECT n FROM Notification n WHERE n.userId = :userId AND (:workspaceIdFilter IS NULL OR"
          + " n.workspaceId = :workspaceIdFilter) AND (:unreadOnly = false OR n.readAt IS NULL)"
          + " AND "
          + VISIBLE_SCOPE_CONDITION
          + " ORDER BY n.createdAt DESC, n.id DESC")
  List<Notification> findVisibleFirstPage(
      @Param("userId") UUID userId,
      @Param("workspaceIdFilter") UUID workspaceIdFilter,
      @Param("unreadOnly") boolean unreadOnly,
      Pageable pageable);

  @Query(
      "SELECT n FROM Notification n WHERE n.userId = :userId AND (:workspaceIdFilter IS NULL OR"
          + " n.workspaceId = :workspaceIdFilter) AND (:unreadOnly = false OR n.readAt IS NULL)"
          + " AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.id <"
          + " :cursorId)) AND "
          + VISIBLE_SCOPE_CONDITION
          + " ORDER BY n.createdAt DESC, n.id DESC")
  List<Notification> findVisibleOlderThan(
      @Param("userId") UUID userId,
      @Param("workspaceIdFilter") UUID workspaceIdFilter,
      @Param("unreadOnly") boolean unreadOnly,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  @Query(
      "SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.readAt IS NULL AND "
          + VISIBLE_SCOPE_CONDITION)
  long countVisibleUnread(@Param("userId") UUID userId);

  @Query(
      "SELECT n FROM Notification n WHERE n.id = :notificationId AND n.userId = :userId AND "
          + VISIBLE_SCOPE_CONDITION)
  Optional<Notification> findVisibleByIdAndUserId(
      @Param("notificationId") UUID notificationId, @Param("userId") UUID userId);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL"
          + " AND (:workspaceIdFilter IS NULL OR n.workspaceId = :workspaceIdFilter) AND "
          + VISIBLE_SCOPE_CONDITION)
  int markAllVisibleRead(
      @Param("userId") UUID userId,
      @Param("workspaceIdFilter") UUID workspaceIdFilter,
      @Param("now") Instant now);
}
