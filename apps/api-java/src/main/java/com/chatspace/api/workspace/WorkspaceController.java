package com.chatspace.api.workspace;

import com.chatspace.api.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ワークスペース機能定義書§4の使用APIに対応する。 */
@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  public WorkspaceController(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @PostMapping
  public ResponseEntity<WorkspaceResponse> create(
      @Valid @RequestBody CreateWorkspaceRequest request, @CurrentUser UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(workspaceService.create(request.name(), userId));
  }

  @GetMapping
  public List<WorkspaceResponse> list(@CurrentUser UUID userId) {
    return workspaceService.listForUser(userId);
  }

  @GetMapping("/{workspaceId}/members")
  public List<WorkspaceMemberResponse> members(
      @PathVariable UUID workspaceId, @CurrentUser UUID userId) {
    return workspaceService.listMembers(workspaceId, userId);
  }

  @GetMapping("/{workspaceId}/presence")
  public List<UUID> presence(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
    return workspaceService.presence(workspaceId, userId);
  }

  @PostMapping("/{workspaceId}/invite")
  public ResponseEntity<Void> invite(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody InviteWorkspaceMemberRequest request,
      @CurrentUser UUID userId) {
    workspaceService.invite(workspaceId, userId, request.userId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/{workspaceId}/kick")
  public ResponseEntity<Void> kick(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody KickWorkspaceMemberRequest request,
      @CurrentUser UUID userId) {
    workspaceService.kick(workspaceId, userId, request.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{workspaceId}/leave")
  public ResponseEntity<Void> leave(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
    workspaceService.leave(workspaceId, userId);
    return ResponseEntity.noContent().build();
  }
}
