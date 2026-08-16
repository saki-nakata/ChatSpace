package com.chatspace.api.message;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

  Optional<Attachment> findByStorageKey(String storageKey);
}
