package com.chatspace.api.dm;

import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DM機能定義書§3の業務ロジック。 */
@Service
public class DmService {

  private static final int PREVIEW_MAX_LENGTH = 100;

  private final DmThreadRepository dmThreadRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final UserRepository userRepository;
  private final MessageRepository messageRepository;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;

  public DmService(
      DmThreadRepository dmThreadRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      UserRepository userRepository,
      MessageRepository messageRepository,
      WorkspaceAuthorizationService workspaceAuthorizationService,
      DmAuthorizationService dmAuthorizationService) {
    this.dmThreadRepository = dmThreadRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.userRepository = userRepository;
    this.messageRepository = messageRepository;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
  }

  @Transactional(readOnly = true)
  public List<DmThreadResponse> list(UUID workspaceId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    List<DmThread> threads = dmThreadRepository.findAllForUser(workspaceId, callerId);

    Map<UUID, User> otherUsers = new LinkedHashMap<>();
    userRepository
        .findAllById(threads.stream().map(t -> otherUserId(t, callerId)).toList())
        .forEach(u -> otherUsers.put(u.getId(), u));

    return threads.stream().map(thread -> toResponse(thread, callerId, otherUsers)).toList();
  }

  @Transactional
  public DmThreadResponse getOrCreate(UUID workspaceId, UUID callerId, String targetUserId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    User target =
        userRepository
            .findByUserId(targetUserId)
            .orElseThrow(() -> new NotFoundException("指定されたユーザーが見つかりません。"));
    if (target.getId().equals(callerId)) {
      throw new BadRequestException("自分自身とはDMできません。");
    }
    if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, target.getId())) {
      throw new NotFoundException("指定されたユーザーはこのワークスペースのメンバーではありません。");
    }

    UUID userAId = callerId.compareTo(target.getId()) < 0 ? callerId : target.getId();
    UUID userBId = callerId.compareTo(target.getId()) < 0 ? target.getId() : callerId;
    DmThread thread =
        dmThreadRepository
            .findByWorkspaceIdAndUserAIdAndUserBId(workspaceId, userAId, userBId)
            .orElseGet(() -> dmThreadRepository.save(new DmThread(workspaceId, userAId, userBId)));

    return toResponse(thread, callerId, Map.of(target.getId(), target));
  }

  @Transactional
  public void markRead(UUID workspaceId, UUID dmId, UUID callerId) {
    DmThread thread = dmAuthorizationService.requireDmAccess(dmId, callerId, workspaceId);
    thread.markRead(callerId, Instant.now());
    dmThreadRepository.save(thread);
  }

  private DmThreadResponse toResponse(
      DmThread thread, UUID callerId, Map<UUID, User> otherUsersById) {
    UUID otherUserId = otherUserId(thread, callerId);
    User otherUser = otherUsersById.get(otherUserId);
    Instant myLastReadAt =
        callerId.equals(thread.getUserAId()) ? thread.getLastReadAtA() : thread.getLastReadAtB();
    long unreadCount = messageRepository.countUnreadInDm(thread.getId(), callerId, myLastReadAt);
    String preview =
        messageRepository
            .findFirstByDmIdAndDeletedAtIsNullOrderByCreatedAtDesc(thread.getId())
            .map(this::toPreview)
            .orElse(null);
    return DmThreadResponse.of(thread, otherUser, unreadCount, preview);
  }

  private String toPreview(Message message) {
    String body = message.getBody();
    return body.length() > PREVIEW_MAX_LENGTH ? body.substring(0, PREVIEW_MAX_LENGTH) : body;
  }

  private UUID otherUserId(DmThread thread, UUID callerId) {
    return thread.getUserAId().equals(callerId) ? thread.getUserBId() : thread.getUserAId();
  }
}
