package com.chatspace.api.notification;

import com.chatspace.api.common.CurrentUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知機能定義書§4の使用APIに対応する。ワークスペース/チャンネル配下ではなくユーザー単位のトップレベルリソース。 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public NotificationListResponse list(
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
      @RequestParam(required = false) Instant cursorCreatedAt,
      @RequestParam(required = false) UUID cursorId,
      @CurrentUser UUID userId) {
    return notificationService.list(userId, workspaceId, unreadOnly, cursorCreatedAt, cursorId);
  }

  @GetMapping("/unread-count")
  public Map<String, Long> unreadCount(@CurrentUser UUID userId) {
    return Map.of("unreadCount", notificationService.unreadCount(userId));
  }

  @PostMapping("/{notificationId}/read")
  public void markRead(@PathVariable UUID notificationId, @CurrentUser UUID userId) {
    notificationService.markRead(userId, notificationId);
  }

  @PostMapping("/read-all")
  public void markAllRead(
      @RequestParam(required = false) UUID workspaceId, @CurrentUser UUID userId) {
    notificationService.markAllRead(userId, workspaceId);
  }
}
