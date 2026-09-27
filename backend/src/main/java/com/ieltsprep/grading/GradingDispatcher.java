package com.ieltsprep.grading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs grading jobs off the request thread (virtual threads) once the submitting transaction has committed.
 * With {@code ielts.grading.async=false} (tests) the caller waits for the job to finish.
 */
@Component
public class GradingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GradingDispatcher.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final boolean async;

    public GradingDispatcher(@Value("${ielts.grading.async:true}") boolean async) {
        this.async = async;
    }

    public void dispatch(String label, Runnable job) {
        Runnable safe = () -> {
            try {
                job.run();
            } catch (Exception e) {
                log.error("Background job {} failed", label, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(safe);
                }
            });
        } else {
            submit(safe);
        }
    }

    /**
     * Always runs on a separate (virtual) thread so the job gets its own transactions — work done inside afterCommit on
     * the submitting thread would join the finished transaction and never be committed. Synchronous mode waits for it.
     */
    private void submit(Runnable job) {
        var future = executor.submit(job);
        if (!async) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Inline job failed", e.getCause());
            }
        }
    }
}
