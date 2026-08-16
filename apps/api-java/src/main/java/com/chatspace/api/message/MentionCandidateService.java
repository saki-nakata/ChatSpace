package com.chatspace.api.message;

import com.chatspace.api.channel.ChannelMember;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.user.UserResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * メンション自動補完(候補一覧取得、メンション機能定義書§3.3・§6)。
 *
 * <p>絞り込み元は対象チャンネルの{@code ChannelMember}のみ(ワークスペース全体のメンバー一覧や他チャンネルのメンバーを
 * 含めてはならない。候補一覧そのものがメンバー構成の情報源になり得るため、チャンネル機能同様のスコープ制約を負う)。
 */
@Service
public class MentionCandidateService {

  private static final int MAX_CANDIDATES = 20;

  private final ChannelMemberRepository channelMemberRepository;
  private final UserRepository userRepository;

  public MentionCandidateService(
      ChannelMemberRepository channelMemberRepository, UserRepository userRepository) {
    this.channelMemberRepository = channelMemberRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> findCandidates(UUID channelId, String prefix) {
    List<ChannelMember> members =
        channelMemberRepository.findByChannelIdOrderByJoinedAtAsc(channelId);
    List<User> users =
        userRepository.findAllById(members.stream().map(ChannelMember::getUserId).toList());

    String needle = prefix == null ? "" : prefix.toLowerCase();
    return users.stream()
        .filter(user -> user.getUserId().toLowerCase().startsWith(needle))
        .sorted(Comparator.comparing(User::getUserId))
        .limit(MAX_CANDIDATES)
        .map(UserResponse::from)
        .toList();
  }
}
