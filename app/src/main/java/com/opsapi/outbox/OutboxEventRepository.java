package com.opsapi.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // simple: read oldest unsent rows
    @Query("select e from OutboxEventEntity e where e.sentAt is null order by e.createdAt asc")
    List<OutboxEventEntity> findUnsentOrdered();
}
