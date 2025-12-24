package com.opsapi.notes;

import com.opsapi.notes.dto.NoteCreateRequest;
import com.opsapi.notes.dto.NoteListResponse;
import com.opsapi.notes.dto.NoteResponse;
import com.opsapi.tasks.TaskNotFoundException;
import com.opsapi.tasks.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

    private final TaskRepository taskRepo;
    private final NoteRepository noteRepo;

    public NoteService(TaskRepository taskRepo, NoteRepository noteRepo) {
        this.taskRepo = taskRepo;
        this.noteRepo = noteRepo;
    }

    public NoteResponse createNote(UUID taskId, NoteCreateRequest req) {
        if (!taskRepo.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }

        NoteEntity saved = noteRepo.save(new NoteEntity(
                UUID.randomUUID(),
                taskId,
                req.getBody().trim()
        ));

        return toResponse(saved);
    }

    public NoteListResponse listNotes(UUID taskId) {
        if (!taskRepo.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }

        List<NoteResponse> items = noteRepo.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();

        return new NoteListResponse(items.size(), items);
    }

    private NoteResponse toResponse(NoteEntity n) {
        return new NoteResponse(
                n.getId(),
                n.getTaskId(),
                n.getBody(),
                n.getCreatedAt()
        );
    }
}