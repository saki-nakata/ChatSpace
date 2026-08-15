package com.chatspace.api.message;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  @Query(
      "SELECT COUNT(m) FROM Message m WHERE m.channelId = :channelId AND m.authorId <>"
          + " :userId AND m.deletedAt IS NULL AND m.createdAt > :lastReadAt")
  long countUnreadInChannel(
      @Param("channelId") UUID channelId,
      @Param("userId") UUID userId,
      @Param("lastReadAt") Instant lastReadAt);

  @Query(
      "SELECT COUNT(m) FROM Message m WHERE m.dmId = :dmId AND m.authorId <> :userId AND"
          + " m.deletedAt IS NULL AND m.createdAt > :lastReadAt")
  long countUnreadInDm(
      @Param("dmId") UUID dmId,
      @Param("userId") UUID userId,
      @Param("lastReadAt") Instant lastReadAt);

  Optional<Message> findFirstByDmIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID dmId);
}
