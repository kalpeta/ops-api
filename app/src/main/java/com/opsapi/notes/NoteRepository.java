package com.opsapi.notes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    List<NoteEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}