package com.chatspace.api.channel;

import com.chatspace.api.common.ConflictException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.realtime.MemberKickedEvent;
import com.chatspace.api.realtime.RealtimeEventPublisher;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** チャンネル機能定義書§3の業務ロジック。 */
@Service
public class ChannelService {

  private final ChannelRepository channelRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final UserRepository userRepository;
  private final MessageRepository messageRepository;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;
  private final ChannelAuthorizationService channelAuthorizationService;
  private final ApplicationEventPublisher eventPublisher;
  private final RealtimeEventPublisher realtimeEventPublisher;

  public ChannelService(
      ChannelRepository channelRepository,
      ChannelMemberRepository channelMemberRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      UserRepository userRepository,
      MessageRepository messageRepository,
      WorkspaceAuthorizationService workspaceAuthorizationService,
      ChannelAuthorizationService channelAuthorizationService,
      ApplicationEventPublisher eventPublisher,
      RealtimeEventPublisher realtimeEventPublisher) {
    this.channelRepository = channelRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.userRepository = userRepository;
    this.messageRepository = messageRepository;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
    this.channelAuthorizationService = channelAuthorizationService;
    this.eventPublisher = eventPublisher;
    this.realtimeEventPublisher = realtimeEventPublisher;
  }

  @Transactional
  public ChannelResponse create(
      UUID workspaceId, UUID callerId, String name, ChannelType type, List<String> memberUserIds) {
    workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    if (channelRepository.existsByWorkspaceIdAndName(workspaceId, name)) {
      throw new ConflictException("同名のチャンネルが既に存在します。");
    }
    Channel channel = channelRepository.save(new Channel(workspaceId, name, type));
    channelMemberRepository.save(new ChannelMember(channel.getId(), callerId));

    if (type == ChannelType.PRIVATE && memberUserIds != null) {
      for (String handle : memberUserIds) {
        userRepository
            .findByUserId(handle)
            .filter(user -> !user.getId().equals(callerId))
            .filter(
                user ->
                    workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                        workspaceId, user.getId()))
            .filter(
                user ->
                    channelMemberRepository
                        .findByChannelIdAndUserId(channel.getId(), user.getId())
                        .isEmpty())
            .ifPresent(
                user ->
                    channelMemberRepository.save(new ChannelMember(channel.getId(), user.getId())));
      }
    }
    ChannelResponse response = ChannelResponse.from(channel, true, 0);
    realtimeEventPublisher.channelCreated(workspaceId, response);
    return response;
  }

  @Transactional(readOnly = true)
  public List<ChannelResponse> list(UUID workspaceId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);

    List<Channel> publicChannels =
        channelRepository.findByWorkspaceIdAndType(workspaceId, ChannelType.PUBLIC);
    List<ChannelMember> myMemberships =
        channelMemberRepository.findByUserIdAndWorkspaceId(callerId, workspaceId);
    Map<UUID, ChannelMember> membershipByChannelId = new LinkedHashMap<>();
    myMemberships.forEach(m -> membershipByChannelId.put(m.getChannelId(), m));

    Map<UUID, Channel> channelsById = new LinkedHashMap<>();
    publicChannels.forEach(c -> channelsById.put(c.getId(), c));
    List<UUID> missingChannelIds =
        myMemberships.stream()
            .map(ChannelMember::getChannelId)
            .filter(id -> !channelsById.containsKey(id))
            .toList();
    channelRepository.findAllById(missingChannelIds).forEach(c -> channelsById.put(c.getId(), c));

    return channelsById.values().stream()
        .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
        .map(
            channel -> {
              ChannelMember membership = membershipByChannelId.get(channel.getId());
              boolean isMember = membership != null;
              long unreadCount =
                  isMember
                      ? messageRepository.countUnreadInChannel(
                          channel.getId(), callerId, membership.getLastReadAt())
                      : 0;
              return ChannelResponse.from(channel, isMember, unreadCount);
            })
        .toList();
  }

  @Transactional
  public void join(UUID workspaceId, UUID channelId, UUID callerId) {
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    Channel channel =
        channelRepository
            .findByIdAndWorkspaceId(channelId, workspaceId)
            .orElseThrow(() -> new NotFoundException("チャンネルが見つかりません。"));
    if (channel.getType() == ChannelType.PRIVATE) {
      // 非メンバーによるプライベートチャンネルへのjoin試行は、存在秘匿のため404で統一する(チャンネル機能定義書§3.3)
      throw new NotFoundException("チャンネルが見つかりません。");
    }
    if (channelMemberRepository.findByChannelIdAndUserId(channelId, callerId).isEmpty()) {
      channelMemberRepository.save(new ChannelMember(channelId, callerId));
    }
  }

  @Transactional
  public void markRead(UUID workspaceId, UUID channelId, UUID callerId) {
    channelAuthorizationService.requireChannelMember(channelId, callerId, workspaceId);
    ChannelMember membership =
        channelMemberRepository
            .findByChannelIdAndUserId(channelId, callerId)
            .orElseThrow(() -> new NotFoundException("チャンネルが見つかりません。"));
    membership.markRead(Instant.now());
    channelMemberRepository.save(membership);
  }

  @Transactional
  public void invite(UUID workspaceId, UUID channelId, UUID callerId, String targetUserId) {
    workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    channelRepository
        .findByIdAndWorkspaceId(channelId, workspaceId)
        .orElseThrow(() -> new NotFoundException("チャンネルが見つかりません。"));
    User target =
        userRepository
            .findByUserId(targetUserId)
            .orElseThrow(() -> new NotFoundException("指定されたユーザーが見つかりません。"));
    if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, target.getId())) {
      throw new NotFoundException("指定されたユーザーはこのワークスペースのメンバーではありません。");
    }
    if (channelMemberRepository.findByChannelIdAndUserId(channelId, target.getId()).isEmpty()) {
      channelMemberRepository.save(new ChannelMember(channelId, target.getId()));
    }
    // TODO(フェーズ5): 招待されたユーザーへCHANNEL_INVITE通知を送信する(通知機能定義書参照)
  }

  @Transactional(readOnly = true)
  public List<ChannelMemberResponse> listMembers(UUID workspaceId, UUID channelId, UUID callerId) {
    channelAuthorizationService.requireChannelMember(channelId, callerId, workspaceId);
    List<ChannelMember> members =
        channelMemberRepository.findByChannelIdOrderByJoinedAtAsc(channelId);
    Map<UUID, User> users = new LinkedHashMap<>();
    userRepository
        .findAllById(members.stream().map(ChannelMember::getUserId).toList())
        .forEach(u -> users.put(u.getId(), u));
    return members.stream()
        .map(m -> ChannelMemberResponse.from(m, users.get(m.getUserId())))
        .toList();
  }

  @Transactional
  public void removeMember(UUID workspaceId, UUID channelId, UUID callerId, UUID targetUserId) {
    channelAuthorizationService.requireChannelMember(channelId, callerId, workspaceId);
    if (!targetUserId.equals(callerId)) {
      workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    }
    ChannelMember target =
        channelMemberRepository
            .findByChannelIdAndUserId(channelId, targetUserId)
            .orElseThrow(() -> new NotFoundException("対象のメンバーが見つかりません。"));
    channelMemberRepository.delete(target);
    if (!targetUserId.equals(callerId)) {
      // オーナーによる強制退出の場合のみ、コミット後に強制切断する(自主退出では発行しない)
      eventPublisher.publishEvent(new MemberKickedEvent(targetUserId));
      realtimeEventPublisher.channelMemberKicked(
          workspaceId, Map.of("channelId", channelId, "userId", targetUserId));
    }
  }

  @Transactional
  public void delete(UUID workspaceId, UUID channelId, UUID callerId) {
    workspaceAuthorizationService.requireOwner(workspaceId, callerId);
    Channel channel =
        channelRepository
            .findByIdAndWorkspaceId(channelId, workspaceId)
            .orElseThrow(() -> new NotFoundException("チャンネルが見つかりません。"));
    // 子リソース(ChannelMember/Message等)はDB側のON DELETE CASCADEで削除される(DB設計書参照)
    channelRepository.delete(channel);
    realtimeEventPublisher.channelDeleted(workspaceId, Map.of("channelId", channelId));
  }
}
