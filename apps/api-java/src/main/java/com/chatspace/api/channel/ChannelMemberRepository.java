package com.chatspace.api.channel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {

  Optional<ChannelMember> findByChannelIdAndUserId(UUID channelId, UUID userId);

  List<ChannelMember> findByChannelIdOrderByJoinedAtAsc(UUID channelId);

  void deleteByChannelIdAndUserId(UUID channelId, UUID userId);

  @Query(
      "SELECT cm FROM ChannelMember cm WHERE cm.userId = :userId AND cm.channelId IN"
          + " (SELECT c.id FROM Channel c WHERE c.workspaceId = :workspaceId)")
  List<ChannelMember> findByUserIdAndWorkspaceId(
      @Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId);

  /** ワークスペースからのキック・自主退出時、当該ワークスペース内の全チャンネルメンバーシップを道連れで削除する(ワークスペース機能定義書§3.6)。 */
  @Modifying
  @Query(
      "DELETE FROM ChannelMember cm WHERE cm.userId = :userId AND cm.channelId IN"
          + " (SELECT c.id FROM Channel c WHERE c.workspaceId = :workspaceId)")
  void deleteByUserIdAndWorkspaceId(
      @Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId);
}
