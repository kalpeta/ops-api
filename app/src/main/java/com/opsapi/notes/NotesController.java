package com.opsapi.notes;

import com.opsapi.notes.dto.NoteCreateRequest;
import com.opsapi.notes.dto.NoteListResponse;
import com.opsapi.notes.dto.NoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class NotesController {

    private final NoteService service;

    public NotesController(NoteService service) {
        this.service = service;
    }

    @PostMapping("/tasks/{taskId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@PathVariable UUID taskId, @Valid @RequestBody NoteCreateRequest req) {
        return service.createNote(taskId, req);
    }

    @GetMapping("/tasks/{taskId}/notes")
    public NoteListResponse list(@PathVariable UUID taskId) {
        return service.listNotes(taskId);
    }
}