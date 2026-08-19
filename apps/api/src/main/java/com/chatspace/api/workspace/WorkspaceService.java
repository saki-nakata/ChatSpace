package com.chatspace.api.workspace;

import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.ConflictException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.notification.NotificationService;
import com.chatspace.api.notification.NotificationType;
import com.chatspace.api.realtime.MemberKickedEvent;
import com.chatspace.api.realtime.PresenceService;
import com.chatspace.api.realtime.RealtimeEventPublisher;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ワークスペース機能定義書§3の業務ロジック。 */
@Service
public class WorkspaceService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final UserRepository userRepository;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;
  private final PresenceService presenceService;
  private final ApplicationEventPublisher eventPublisher;
  private final RealtimeEventPublisher realtimeEventPublisher;
  private final NotificationService notificationService;

  public WorkspaceService(
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      ChannelMemberRepository channelMemberRepository,
      UserRepository userRepository,
      WorkspaceAuthorizationService workspaceAuthorizationService,
      PresenceService presenceService,
      ApplicationEventPublisher eventPublisher,
      RealtimeEventPublisher realtimeEventPublisher,
      NotificationService notificationService) {
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.userRepository = userRepository;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
    this.presenceService = presenceService;
    this.eventPublisher = eventPublisher;
    this.realtimeEventPublisher = realtimeEventPublisher;
    this.notificationService = notificationService;
  }

  @Transactional
  public WorkspaceResponse create(String name, UUID ownerId) {
    Workspace workspace = workspaceRepository.save(new Workspace(name, ownerId));
    workspaceMemberRepository.save(
        new WorkspaceMember(workspace.getId(), ownerId, WorkspaceRole.OWNER));
    return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
  }

  @Transactional(readOnly = true)
  public List<WorkspaceResponse> listForUser(UUID userId) {
    List<WorkspaceMember> memberships =
        workspaceMemberRepository.findByUserIdOrderByJoinedAtAsc(userId);
    Map<UUID, Workspace> workspaces = new LinkedHashMap<>();
    workspaceRepository
        .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList())
        .forEach(workspace -> workspaces.put(workspace.getId(), workspace));
    return memberships.stream()
        .map(m -> WorkspaceResponse.from(workspaces.get(m.getWorkspaceId()), m.getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    List<WorkspaceMember> members =
        workspaceMemberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId);
    Map<UUID, User> users = new LinkedHashMap<>();
    userRepository
        .findAllById(members.stream().map(WorkspaceMember::getUserId).toList())
        .forEach(user -> users.put(user.getId(), user));
    return members.stream()
        .map(m -> WorkspaceMemberResponse.from(m, users.get(m.getUserId())))
        .toList();
  }

  /** 現在オンラインのユーザーID一覧(リアルタイム通信機能定義書§11)。 */
  @Transactional(readOnly = true)
  public List<UUID> presence(UUID workspaceId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    return workspaceMemberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId).stream()
        .map(WorkspaceMember::getUserId)
        .filter(presenceService::isOnline)
        .toList();
  }

  @Transactional
  public void invite(UUID workspaceId, UUID callerId, String targetUserId) {
    workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    User target =
        userRepository
            .findByUserId(targetUserId)
            .orElseThrow(() -> new NotFoundException("指定されたユーザーが見つかりません。"));
    if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, target.getId())) {
      throw new ConflictException("既にワークスペースのメンバーです。");
    }
    workspaceMemberRepository.save(
        new WorkspaceMember(workspaceId, target.getId(), WorkspaceRole.MEMBER));
    notificationService.notify(
        NotificationType.WORKSPACE_INVITE,
        target.getId(),
        callerId,
        workspaceId,
        null,
        null,
        null,
        null);
  }

  @Transactional
  public void kick(UUID workspaceId, UUID callerId, UUID targetUserId) {
    workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    WorkspaceMember target =
        workspaceMemberRepository
            .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
            .orElseThrow(() -> new NotFoundException("対象のメンバーが見つかりません。"));
    if (target.getRole() == WorkspaceRole.OWNER) {
      throw new BadRequestException("オーナーをキックすることはできません。");
    }
    channelMemberRepository.deleteByUserIdAndWorkspaceId(targetUserId, workspaceId);
    workspaceMemberRepository.delete(target);
    // 強制切断はコミット後に実行する(MemberKickedEventListenerが@TransactionalEventListener(AFTER_COMMIT)で処理)
    eventPublisher.publishEvent(new MemberKickedEvent(targetUserId));
    realtimeEventPublisher.workspaceMemberKicked(workspaceId, Map.of("userId", targetUserId));
  }

  @Transactional
  public void leave(UUID workspaceId, UUID callerId) {
    WorkspaceMember member = workspaceAuthorizationService.requireMember(workspaceId, callerId);
    if (member.getRole() == WorkspaceRole.OWNER) {
      throw new BadRequestException("オーナーはワークスペースから退出できません。");
    }
    channelMemberRepository.deleteByUserIdAndWorkspaceId(callerId, workspaceId);
    workspaceMemberRepository.delete(member);
  }
}
