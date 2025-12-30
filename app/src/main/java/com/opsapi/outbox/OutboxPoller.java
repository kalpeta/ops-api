package com.opsapi.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "customer-events";

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean simulateCrashAfterSendOnce;
    private final AtomicBoolean crashFired = new AtomicBoolean(false);

    public OutboxPoller(OutboxEventRepository outboxRepo,
        KafkaTemplate<String, String> kafkaTemplate,
        @Value("${ops.outbox.simulate-crash-after-send-once:false}") boolean simulateCrashAfterSendOnce) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.simulateCrashAfterSendOnce = simulateCrashAfterSendOnce;
    }

    @Scheduled(fixedDelayString = "${ops.outbox.poller.delay-ms:1000}")
    @Transactional
    public void poll() {
        List<OutboxEventEntity> unsent = outboxRepo.findUnsentOrdered();
        if (unsent.isEmpty()) return;

        int batchSize = Integer.parseInt(System.getProperty("ops.outbox.batchSize", "50"));
        int sentCount = 0;

        for (OutboxEventEntity e : unsent.stream().limit(batchSize).toList()) {
            try {
                // key = aggregateId for per-customer ordering
                String key = e.getAggregateId().toString();

                kafkaTemplate.send(TOPIC, key, e.getPayload());
                if (simulateCrashAfterSendOnce && crashFired.compareAndSet(false, true)) {
                    log.error("OUTBOX_SIMULATED_CRASH_AFTER_SEND id={} (Kafka sent, DB not marked sent yet)", e.getId());
                    throw new RuntimeException("SIMULATED_CRASH_AFTER_SEND");
                }
                e.markSent();
                sentCount++;

                log.info("OUTBOX_SENT id={} eventType={} key={} attempts={} corr={}",
                        e.getId(), e.getEventType(), key, e.getAttempts(), e.getCorrelationId());

            } catch (Exception ex) {
                e.markFailed(ex.toString());
                log.warn("OUTBOX_SEND_FAILED id={} eventType={} attempts={} corr={} err={}",
                        e.getId(), e.getEventType(), e.getAttempts(), e.getCorrelationId(), ex.toString());

                // do NOT throw -> keep poller alive
            }
        }

        // entity updates will flush at transaction end
        if (sentCount > 0) {
            log.info("OUTBOX_BATCH_DONE sent={}", sentCount);
        }
    }
}
