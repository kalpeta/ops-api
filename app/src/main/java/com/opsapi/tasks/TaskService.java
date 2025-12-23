package com.opsapi.tasks;

import com.opsapi.customers.CustomerNotFoundException;
import com.opsapi.customers.CustomerRepository;
import com.opsapi.notes.NoteEntity;
import com.opsapi.notes.NoteRepository;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TaskService {

    private final CustomerRepository customerRepo;
    private final TaskRepository taskRepo;
    private final NoteRepository noteRepo;

    public TaskService(CustomerRepository customerRepo, TaskRepository taskRepo, NoteRepository noteRepo) {
        this.customerRepo = customerRepo;
        this.taskRepo = taskRepo;
        this.noteRepo = noteRepo;
    }

    @Transactional
    public TaskResponse createTaskForCustomer(UUID customerId, TaskCreateRequest req) {
        // Rule: customer must exist
        if (!customerRepo.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        UUID taskId = UUID.randomUUID();

        TaskEntity task = new TaskEntity(
                taskId,
                customerId,
                req.getTitle().trim(),
                TaskStatus.OPEN
        );

        TaskEntity savedTask = taskRepo.save(task);

        // TEMP: simulate failure after task insert to prove rollback works
        if (req.isSimulateFailure()) {
            throw new RuntimeException("Simulated failure after task insert");
        }

        NoteEntity note = new NoteEntity(
                UUID.randomUUID(),
                taskId,
                "Task created"
        );

        noteRepo.save(note);

        return new TaskResponse(
                savedTask.getId(),
                savedTask.getCustomerId(),
                savedTask.getTitle(),
                savedTask.getStatus(),
                savedTask.getCreatedAt(),
                savedTask.getUpdatedAt()
        );
    }
}