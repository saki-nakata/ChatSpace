package com.chatspace.api.dm;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmThreadRepository extends JpaRepository<DmThread, UUID> {}
