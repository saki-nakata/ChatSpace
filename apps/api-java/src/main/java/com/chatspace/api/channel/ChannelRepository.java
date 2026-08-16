package com.chatspace.api.channel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

  boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);

  List<Channel> findByWorkspaceIdAndType(UUID workspaceId, ChannelType type);

  Optional<Channel> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
