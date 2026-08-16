package com.chatspace.api.message;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** メンション機能定義書§4の候補取得APIに対応する。 */
@RestController
public class MentionController {

  private final ChannelAuthorizationService channelAuthorizationService;
  private final MentionCandidateService mentionCandidateService;

  public MentionController(
      ChannelAuthorizationService channelAuthorizationService,
      MentionCandidateService mentionCandidateService) {
    this.channelAuthorizationService = channelAuthorizationService;
    this.mentionCandidateService = mentionCandidateService;
  }

  @GetMapping("/workspaces/{workspaceId}/channels/{channelId}/mentions/candidates")
  public MentionCandidatesResponse candidates(
      @PathVariable UUID workspaceId,
      @PathVariable UUID channelId,
      @RequestParam(required = false) String q,
      @CurrentUser UUID userId) {
    channelAuthorizationService.requireChannelMember(channelId, userId, workspaceId);
    return new MentionCandidatesResponse(mentionCandidateService.findCandidates(channelId, q));
  }
}
