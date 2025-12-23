package com.opsapi.tasks;

import com.opsapi.customers.CustomerNotFoundException;
import com.opsapi.customers.CustomerRepository;
import com.opsapi.notes.NoteEntity;
import com.opsapi.notes.NoteRepository;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskListResponse;
import com.opsapi.tasks.dto.TaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

        if (req.isSimulateFailure()) {
            throw new RuntimeException("Simulated failure after task insert");
        }

        NoteEntity note = new NoteEntity(
                UUID.randomUUID(),
                taskId,
                "Task created"
        );

        noteRepo.save(note);

        return toResponse(savedTask);
    }

    public TaskListResponse listTasksForCustomer(UUID customerId) {
        if (!customerRepo.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        List<TaskEntity> tasks = taskRepo.findByCustomerIdOrderByCreatedAtDesc(customerId);

        List<TaskResponse> items = tasks.stream()
                .map(this::toResponse)
                .toList();

        return new TaskListResponse(items.size(), items);
    }

    private TaskResponse toResponse(TaskEntity t) {
        return new TaskResponse(
                t.getId(),
                t.getCustomerId(),
                t.getTitle(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}