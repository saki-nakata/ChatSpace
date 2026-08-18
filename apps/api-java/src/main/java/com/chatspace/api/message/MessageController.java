package com.chatspace.api.message;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.CurrentUser;
import com.chatspace.api.dm.DmAuthorizationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * メッセージング機能定義書§4・リアクション機能定義書§4の使用APIに対応する。
 *
 * <p>チャンネルメッセージ({@code /workspaces/{workspaceId}/channels/{channelId}/messages/**})とDMメッセージ ({@code
 * /workspaces/{workspaceId}/dms/{dmId}/messages/**})は同一の {@link MessageService} を共有するため、
 * 1つのコントローラでSpring MVCの複数パスパターン+任意path変数({@code channelId}/{@code dmId}のどちらか一方のみが
 * 実際のリクエストで埋まる)により統合する(計画書§3の設計方針通り)。認可の事前チェックはスコープに応じて {@code ChannelAuthorizationService}/{@code
 * DmAuthorizationService} を出し分ける。
 */
@RestController
public class MessageController {

  private static final String CHANNEL_MESSAGES =
      "/workspaces/{workspaceId}/channels/{channelId}/messages";
  private static final String DM_MESSAGES = "/workspaces/{workspaceId}/dms/{dmId}/messages";

  private final MessageService messageService;
  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;

  public MessageController(
      MessageService messageService,
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService) {
    this.messageService = messageService;
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
  }

  @PostMapping({CHANNEL_MESSAGES, DM_MESSAGES})
  public ResponseEntity<MessageResponse> create(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @Valid @RequestBody CreateMessageRequest request,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    MessageResponse response = messageService.create(workspaceId, channelId, dmId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping({CHANNEL_MESSAGES, DM_MESSAGES})
  public MessageListResponse list(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @RequestParam(required = false) Instant cursorCreatedAt,
      @RequestParam(required = false) UUID cursorId,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    return messageService.list(channelId, dmId, userId, cursorCreatedAt, cursorId);
  }

  @GetMapping({CHANNEL_MESSAGES + "/{messageId}/context", DM_MESSAGES + "/{messageId}/context"})
  public MessageContextResponse context(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @PathVariable UUID messageId,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    return messageService.getContext(channelId, dmId, messageId, userId);
  }

  @GetMapping({CHANNEL_MESSAGES + "/{messageId}/replies", DM_MESSAGES + "/{messageId}/replies"})
  public MessageListResponse replies(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @PathVariable UUID messageId,
      @RequestParam(required = false) Instant cursorCreatedAt,
      @RequestParam(required = false) UUID cursorId,
      @RequestParam(required = false) UUID around,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    if (around != null) {
      return messageService.listRepliesAround(channelId, dmId, messageId, around, userId);
    }
    return messageService.listReplies(
        channelId, dmId, messageId, userId, cursorCreatedAt, cursorId);
  }

  @PatchMapping({CHANNEL_MESSAGES + "/{messageId}", DM_MESSAGES + "/{messageId}"})
  public MessageResponse edit(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @PathVariable UUID messageId,
      @Valid @RequestBody EditMessageRequest request,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    return messageService.edit(channelId, dmId, messageId, userId, request);
  }

  @DeleteMapping({CHANNEL_MESSAGES + "/{messageId}", DM_MESSAGES + "/{messageId}"})
  public ResponseEntity<Void> delete(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @PathVariable UUID messageId,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    messageService.delete(channelId, dmId, messageId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping({
    CHANNEL_MESSAGES + "/{messageId}/reactions",
    DM_MESSAGES + "/{messageId}/reactions"
  })
  public MessageResponse toggleReaction(
      @PathVariable UUID workspaceId,
      @PathVariable(required = false) UUID channelId,
      @PathVariable(required = false) UUID dmId,
      @PathVariable UUID messageId,
      @Valid @RequestBody ToggleReactionRequest request,
      @CurrentUser UUID userId) {
    requireScopeAccess(workspaceId, channelId, dmId, userId);
    return messageService.toggleReaction(channelId, dmId, messageId, userId, request.emoji());
  }

  private void requireScopeAccess(UUID workspaceId, UUID channelId, UUID dmId, UUID userId) {
    if (channelId != null) {
      channelAuthorizationService.requireChannelMember(channelId, userId, workspaceId);
    } else {
      dmAuthorizationService.requireDmAccess(dmId, userId, workspaceId);
    }
  }
}
