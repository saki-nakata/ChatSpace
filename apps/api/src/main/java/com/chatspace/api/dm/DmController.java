package com.chatspace.api.dm;

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

/** DM機能定義書§4(DMスレッド自体の管理)の使用APIに対応する。 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/dms")
public class DmController {

  private final DmService dmService;

  public DmController(DmService dmService) {
    this.dmService = dmService;
  }

  @GetMapping
  public List<DmThreadResponse> list(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
    return dmService.list(workspaceId, userId);
  }

  @PostMapping
  public ResponseEntity<DmThreadResponse> getOrCreate(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateDmRequest request,
      @CurrentUser UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(dmService.getOrCreate(workspaceId, userId, request.userId()));
  }

  @PostMapping("/{dmId}/read")
  public ResponseEntity<Void> markRead(
      @PathVariable UUID workspaceId, @PathVariable UUID dmId, @CurrentUser UUID userId) {
    dmService.markRead(workspaceId, dmId, userId);
    return ResponseEntity.noContent().build();
  }
}
