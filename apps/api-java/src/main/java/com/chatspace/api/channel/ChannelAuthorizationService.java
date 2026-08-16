package com.chatspace.api.channel;

import com.chatspace.api.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * チャンネルメンバーシップの認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>チャンネル機能定義書§6を正とする。
 */
@Service
public class ChannelAuthorizationService {

  private final ChannelRepository channelRepository;
  private final ChannelMemberRepository channelMemberRepository;

  public ChannelAuthorizationService(
      ChannelRepository channelRepository, ChannelMemberRepository channelMemberRepository) {
    this.channelRepository = channelRepository;
    this.channelMemberRepository = channelMemberRepository;
  }

  /**
   * 呼び出し元ユーザーが対象チャンネルの現在有効なメンバーであることを検証する。チャンネルが存在しない、{@code workspaceIdOrNull}
   * との不一致(confused-deputy)、非メンバーのいずれも404で統一する(パブリック・プライベートを区別しない)。
   */
  public Channel requireChannelMember(UUID channelId, UUID userId, UUID workspaceIdOrNull) {
    Channel channel = channelRepository.findById(channelId).orElseThrow(this::notFound);
    if (workspaceIdOrNull != null && !channel.getWorkspaceId().equals(workspaceIdOrNull)) {
      throw notFound();
    }
    channelMemberRepository.findByChannelIdAndUserId(channelId, userId).orElseThrow(this::notFound);
    return channel;
  }

  private NotFoundException notFound() {
    return new NotFoundException("チャンネルが見つかりません。");
  }
}
