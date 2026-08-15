package com.chatspace.api.message;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentionRepository extends JpaRepository<Mention, UUID> {}
