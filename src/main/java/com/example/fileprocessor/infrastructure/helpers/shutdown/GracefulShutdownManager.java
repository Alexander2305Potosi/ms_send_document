package com.example.fileprocessor.infrastructure.helpers.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages graceful shutdown of the application.
 * Listens for ContextClosedEvent and coordinates draining of reactive pipelines.
 */
@Component
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    private final AtomicBoolean draining = new AtomicBoolean(false);
    private volatile Instant shutdownStarted;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdownStarted = Instant.now();
        draining.set(true);
        log.info("Graceful shutdown sequence started at {}", shutdownStarted);
    }

    /**
     * Indicates if the application is draining (stopping).
     */
    public boolean isDraining() {
        return draining.get();
    }

    /**
     * Returns the duration since shutdown started.
     */
    public Duration elapsedSinceShutdown() {
        return shutdownStarted != null
            ? Duration.between(shutdownStarted, Instant.now())
            : Duration.ZERO;
    }

    /**
     * Indicates if there is still time to complete work before forced shutdown.
     * Leaves a 5-second margin before SIGKILL.
     *
     * @param terminationGracePeriod total grace period configured
     * @return true if work can continue, false if about to be killed
     */
    public boolean hasRemainingTime(Duration terminationGracePeriod) {
        Duration elapsed = elapsedSinceShutdown();
        Duration deadline = terminationGracePeriod.minusSeconds(5);
        return elapsed.compareTo(deadline) < 0;
    }
}