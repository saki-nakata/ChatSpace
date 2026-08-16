package com.chatspace.api.message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

  Optional<Reaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

  List<Reaction> findByMessageId(UUID messageId);

  List<Reaction> findByMessageIdIn(Collection<UUID> messageIds);
}
