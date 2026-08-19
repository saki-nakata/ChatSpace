package com.chatspace.api.search;

import com.chatspace.api.common.CurrentUser;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 検索機能定義書§4の使用APIに対応する。 */
@RestController
public class SearchController {

  private final SearchService searchService;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;

  public SearchController(
      SearchService searchService, WorkspaceAuthorizationService workspaceAuthorizationService) {
    this.searchService = searchService;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
  }

  @GetMapping("/workspaces/{workspaceId}/search")
  public SearchResponse search(
      @PathVariable UUID workspaceId,
      @RequestParam String q,
      @RequestParam(required = false) UUID channelId,
      @RequestParam(required = false) Instant cursorCreatedAt,
      @RequestParam(required = false) UUID cursorId,
      @CurrentUser UUID userId) {
    // ワークスペース自体の非メンバーは404(存在秘匿、検索機能定義書§5)
    workspaceAuthorizationService.requireMember(workspaceId, userId);
    return searchService.search(workspaceId, userId, q, channelId, cursorCreatedAt, cursorId);
  }
}
