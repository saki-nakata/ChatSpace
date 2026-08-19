package com.chatspace.api.notification;

import com.chatspace.api.common.Cursor;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.realtime.RealtimeEventPublisher;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知機能定義書§3の業務ロジック。§6.1のスコープ再チェックは{@link NotificationRepository}のクエリに実装されている。 */
@Service
public class NotificationService {

  private static final int PAGE_SIZE = 50;

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final RealtimeEventPublisher realtimeEventPublisher;

  public NotificationService(
      NotificationRepository notificationRepository,
      UserRepository userRepository,
      RealtimeEventPublisher realtimeEventPublisher) {
    this.notificationRepository = notificationRepository;
    this.userRepository = userRepository;
    this.realtimeEventPublisher = realtimeEventPublisher;
  }

  @Transactional(readOnly = true)
  public NotificationListResponse list(
      UUID userId,
      UUID workspaceIdFilter,
      boolean unreadOnly,
      Instant cursorCreatedAt,
      UUID cursorId) {
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    List<Notification> notifications =
        (cursorCreatedAt == null || cursorId == null)
            ? notificationRepository.findVisibleFirstPage(
                userId, workspaceIdFilter, unreadOnly, pageable)
            : notificationRepository.findVisibleOlderThan(
                userId, workspaceIdFilter, unreadOnly, cursorCreatedAt, cursorId, pageable);

    List<NotificationResponse> responses =
        notifications.stream().map(NotificationResponse::from).toList();
    Cursor nextCursor =
        notifications.size() == PAGE_SIZE
            ? cursorOf(notifications.get(notifications.size() - 1))
            : null;
    return new NotificationListResponse(responses, nextCursor);
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return notificationRepository.countVisibleUnread(userId);
  }

  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findVisibleByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotFoundException("通知が見つかりません。"));
    notification.markRead(Instant.now());
    notificationRepository.save(notification);
  }

  @Transactional
  public void markAllRead(UUID userId, UUID workspaceIdFilter) {
    notificationRepository.markAllVisibleRead(userId, workspaceIdFilter, Instant.now());
  }

  /**
   * 通知を生成しリアルタイム配信する(通知機能定義書§3.1)。{@code recipientUserId}が{@code fromUserId}と同一の場合は
   * 生成しない(自分自身への通知は不要。スレッド返信で自分の投稿に自分で返信した場合等を含む)。
   */
  @Transactional
  public void notify(
      NotificationType type,
      UUID recipientUserId,
      UUID fromUserId,
      UUID workspaceId,
      UUID channelId,
      UUID dmId,
      UUID messageId,
      UUID threadParentId) {
    if (recipientUserId.equals(fromUserId)) {
      return;
    }
    User fromUser =
        userRepository
            .findById(fromUserId)
            .orElseThrow(() -> new NotFoundException("ユーザーが見つかりません。"));
    String text = composeText(type, fromUser);
    Notification notification =
        notificationRepository.save(
            new Notification(
                recipientUserId,
                type,
                workspaceId,
                channelId,
                dmId,
                messageId,
                threadParentId,
                fromUserId,
                text));
    realtimeEventPublisher.notification(recipientUserId, NotificationResponse.from(notification));
  }

  private String composeText(NotificationType type, User fromUser) {
    String name = fromUser.getDisplayName();
    return switch (type) {
      case MENTION -> name + "さんがあなたをメンションしました";
      case DM -> name + "さんからDMが届きました";
      case THREAD_REPLY -> name + "さんがあなたのスレッドに返信しました";
      case CHANNEL_INVITE -> name + "さんがチャンネルに招待しました";
      case WORKSPACE_INVITE -> name + "さんがワークスペースに招待しました";
    };
  }

  private Cursor cursorOf(Notification notification) {
    return Cursor.of(notification.getCreatedAt(), notification.getId());
  }
}
