package com.opsapi.tasks;

import com.opsapi.tasks.dto.TaskSummaryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    // One query: tasks + latest note (by max createdAt per task)
    @Query("""
        select new com.opsapi.tasks.dto.TaskSummaryItem(
            t.id,
            t.customerId,
            t.title,
            t.status,
            t.updatedAt,
            n.body,
            n.createdAt
        )
        from TaskEntity t
        left join NoteEntity n
            on n.taskId = t.id
            and n.createdAt = (
                select max(n2.createdAt)
                from NoteEntity n2
                where n2.taskId = t.id
            )
        where t.customerId = :customerId
        order by t.createdAt desc
    """)
    List<TaskSummaryItem> findTaskSummaries(UUID customerId);
}