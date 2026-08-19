package com.chatspace.api.channel;

import com.chatspace.api.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** チャンネル機能定義書§4の使用APIに対応する。 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/channels")
public class ChannelController {

  private final ChannelService channelService;

  public ChannelController(ChannelService channelService) {
    this.channelService = channelService;
  }

  @PostMapping
  public ResponseEntity<ChannelResponse> create(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateChannelRequest request,
      @CurrentUser UUID userId) {
    ChannelResponse response =
        channelService.create(
            workspaceId, userId, request.name(), request.type(), request.memberUserIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public List<ChannelResponse> list(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
    return channelService.list(workspaceId, userId);
  }

  @PostMapping("/{channelId}/read")
  public ResponseEntity<Void> markRead(
      @PathVariable UUID workspaceId, @PathVariable UUID channelId, @CurrentUser UUID userId) {
    channelService.markRead(workspaceId, channelId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{channelId}/join")
  public ResponseEntity<Void> join(
      @PathVariable UUID workspaceId, @PathVariable UUID channelId, @CurrentUser UUID userId) {
    channelService.join(workspaceId, channelId, userId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{channelId}/members")
  public ResponseEntity<Void> invite(
      @PathVariable UUID workspaceId,
      @PathVariable UUID channelId,
      @Valid @RequestBody InviteChannelMemberRequest request,
      @CurrentUser UUID userId) {
    channelService.invite(workspaceId, channelId, userId, request.userId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping("/{channelId}/members")
  public List<ChannelMemberResponse> members(
      @PathVariable UUID workspaceId, @PathVariable UUID channelId, @CurrentUser UUID userId) {
    return channelService.listMembers(workspaceId, channelId, userId);
  }

  @DeleteMapping("/{channelId}/members/{targetUserId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID workspaceId,
      @PathVariable UUID channelId,
      @PathVariable UUID targetUserId,
      @CurrentUser UUID userId) {
    channelService.removeMember(workspaceId, channelId, userId, targetUserId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{channelId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID workspaceId, @PathVariable UUID channelId, @CurrentUser UUID userId) {
    channelService.delete(workspaceId, channelId, userId);
    return ResponseEntity.noContent().build();
  }
}
