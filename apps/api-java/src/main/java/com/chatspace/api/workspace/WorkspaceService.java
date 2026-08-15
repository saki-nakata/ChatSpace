package com.chatspace.api.workspace;

import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.ConflictException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  public WorkspaceService(
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      ChannelMemberRepository channelMemberRepository,
      UserRepository userRepository,
      WorkspaceAuthorizationService workspaceAuthorizationService) {
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.userRepository = userRepository;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
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

  /** 現在オンラインのユーザーID一覧。実際のプレゼンス追跡(WebSocketセッション連携)はフェーズ4で実装するため、現時点では常に空。 */
  @Transactional(readOnly = true)
  public List<UUID> presence(UUID workspaceId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    return List.of();
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
    // TODO(フェーズ5): 招待されたユーザーへWORKSPACE_INVITE通知を送信する(通知機能定義書参照)
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
    // TODO(フェーズ4): AFTER_COMMITでの強制切断・リアルタイム通知(リアルタイム通信機能定義書参照)
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
