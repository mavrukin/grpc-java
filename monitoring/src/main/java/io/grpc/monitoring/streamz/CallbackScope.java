package io.grpc.monitoring.streamz;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CycleDetectingLockFactory;
import com.google.common.util.concurrent.CycleDetectingLockFactory.Policies;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * CallbackScope defines smallest possible set of {@code CallbackMetric} instances and
 * associated triggers that must be executed together, ensuring consistent
 * values across those metrics.
 *
 * @author nsakharo@google.com (Nick Sakharov)
 */
@ThreadSafe
class CallbackScope {

    /**
     * This class is used to represent the scope state during trigger execution. At that
     * time single instance of this class is referenced by all CallbackMetric instances
     * associated with the current scope, allowing to validate attempts to set metric
     * values and to ensure consistency of served values.
     */
    static class ExecutionState {
        private final long threadId = Thread.currentThread().getId();
        private final CountDownLatch latch = new CountDownLatch(1);
        private Set<CallbackMetric<?, ?>> allowedMetrics = ImmutableSet.of();

        ExecutionState() {
        }

        /**
         * Blocks the thread if triggers are still being executed.
         */
        void waitIfUpdating() throws InterruptedException {
            latch.await();
        }

        /**
         * Marks that metric values are now consistent and could be accessed.
         */
        void markReady() {
            latch.countDown();
        }

        /**
         * Defines set of metrics that are allowed to be modified. Set by the
         * executed CallbackTrigger instance.
         */
        void setAllowedMetrics(Set<CallbackMetric<?, ?>> allowedMetrics) {
            this.allowedMetrics = allowedMetrics;
        }

        /**
         * Returns true iff metric values are allowed to be modified.
         */
        boolean isMetricUpdatable(CallbackMetric<?, ?> metric) {
            return (Thread.currentThread().getId() == threadId) && allowedMetrics.contains(metric);
        }
    }

    private static final CycleDetectingLockFactory lockFactory =
            CycleDetectingLockFactory.newInstance(Policies.DISABLED);

    // This static lock used to ensure that trigger registration does not happen
    // concurrently with metric processing (and thus potential trigger execution).
    // Basically we hold exclusive lock during trigger registration operations and
    // shared lock during metric processing (starting from the point where first
    // trigger is executed).
    private static final ReadWriteLock registrationLock =
            lockFactory.newReentrantReadWriteLock("Streamz trigger registration lock");

    private final Set<CallbackTrigger> triggers;
    private final Set<CallbackMetric<?, ?>> metrics;

    // This instance lock is used to ensure that all triggers related to this scope
    // will be executed only once - even in case when two or more threads invoke
    // MetricRegistry.applyToMetric???() simultaneously. Basically, every time
    // when system requests scope to execute triggers, we try to obtain exclusive
    // lock. If successful, we execute triggers and then downgrade lock to the shared
    // lock. If not successful, we know that other thread currently executes triggers,
    // thus we just obtain shared lock. We hold shared lock until the end of metric
    // processing to ensure consistent values across all metrics.
    private final ReadWriteLock callbackLock =
            lockFactory.newReentrantReadWriteLock("Streamz callback scope lock");

    private CallbackScope(Set<CallbackTrigger> triggers, Set<CallbackMetric<?, ?>> metrics) {
        this.triggers = ImmutableSet.copyOf(triggers);
        this.metrics = ImmutableSet.copyOf(metrics);
    }

    static void registerTrigger(CallbackTrigger trigger) {
        registrationLock.writeLock().lock();
        try {
            for (CallbackMetric<?, ?> metric : trigger.getMetrics()) {
                metric.addTrigger(trigger);
            }
            // Create a CallbackScope.
            Set<CallbackTrigger> triggerSet = Sets.newHashSet();
            Set<CallbackMetric<?, ?>> metricSet = Sets.newHashSet();
            trigger.populateScopeMembers(triggerSet, metricSet);

            CallbackScope scope = new CallbackScope(triggerSet, metricSet);

            for (CallbackTrigger aTrigger : triggerSet) {
                aTrigger.setScope(scope);
            }
        } finally {
            registrationLock.writeLock().unlock();
        }
    }

    static void deregisterTrigger(CallbackTrigger trigger) {
        registrationLock.writeLock().lock();
        try {
            CallbackScope oldScope = trigger.getScope();
            if (oldScope == null) {
                return;
            }

            // Deregister trigger
            trigger.setScope(null);
            for (CallbackMetric<?, ?> metric : trigger.getMetrics()) {
                metric.removeTrigger(trigger);
            }

            // Create new CallbackScope instances for remaining triggers.
            for (CallbackTrigger aTrigger : oldScope.triggers) {
                if (aTrigger.getScope() == oldScope) {
                    registerTrigger(aTrigger);
                }
            }
        } finally {
            registrationLock.writeLock().unlock();
        }
    }

    /**
     * IMPORTANT: This method acquires locks which must be released by calling {@link #unlock}.
     */
    void lockAndRunTriggers() {
        // Prevent concurrent trigger registration operations.
        registrationLock.readLock().lock();
        // Try to obtain exclusive lock. If we succeed, it means that this scope has not fired
        // triggers yet and we should do so.
        if (callbackLock.writeLock().tryLock()) {
            try {
                // Obtain shared lock for the scope. Lock will be held until caller (read - MetricRegistry)
                // will process all metric values. Since we already have write lock, this will
                // never block.
                callbackLock.readLock().lock();
                ExecutionState executionState = new ExecutionState();
                try {
                    for (CallbackMetric<?, ?> metric : metrics) {
                        metric.reset(executionState);
                    }
                    for (CallbackTrigger trigger : triggers) {
                        trigger.execute(executionState);
                    }
                    // Note: Code below assumes that runtime exceptions caused by the trigger execution will
                    // be suppressed - otherwise we would never publish cells and old values will continue
                    // to be served.
                    // Alternative would be to remove all cell values in the CallbackMetric.reset() - thus
                    // ensuring that in such case metric will lose all values.
                    for (CallbackMetric<?, ?> metric : metrics) {
                        metric.publishCells();
                    }
                } finally {
                    // Release latch, allowing metric instances to provide cell values.
                    executionState.markReady();
                }
            } finally {
                callbackLock.writeLock().unlock();
            }
        } else {
            // Some other thread is currently processing all triggers (we failed to aqquire
            // write lock). Obtain shared lock for the scope. Lock will be held until caller
            //(read - MetricRegistry) will process all metric values. This will block until
            // other thread finishes up running triggers.
            callbackLock.readLock().lock();
        }
    }

    void unlock() {
        // Release all locks, marking that caller no longer interested in
        // processing metric values.
        callbackLock.readLock().unlock();
        registrationLock.readLock().unlock();
    }
}