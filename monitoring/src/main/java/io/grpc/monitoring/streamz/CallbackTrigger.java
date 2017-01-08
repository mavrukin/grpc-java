package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CallbackTrigger allows one or more {@link CallbackMetric} metrics to be updated
 * simultaneously by a single provided Runnable instance, ensuring all those metrics
 * have consistent values at all times. It is also possible to define multiple triggers
 * for each metric - those triggers would all be executed in unspecified order
 * whenever the system requires the metric cell values. Finally each CallbackTrigger
 * can be deactivated using the {@link CallbackTrigger#deregister} method. Once deactivated,
 * it cannot be reused again.
 *
 * <p>CallbackTrigger instance could be invoked by the system as soon as it is created,
 * and will continue to be invoked for its lifetime, or until {@link #deregister} is called.
 *
 * <p>CallbackTrigger should be instantiated using {@link MetricFactory#newTrigger} method.
 * For triggers associated with only a single metric, {@link CallbackMetric#createTrigger}
 * could be used as a shortcut (note that the createTrigger() method will not return
 * a reference to the CallbackTrigger instance - making it impossible to deregister).
 * Finally, all supplied metrics must be created by the same {@link MetricFactory} as
 * trigger itself.
 *
 * <p>For an API example, see {@link MetricFactory#newTrigger} and
 * {@link CallbackMetric#createTrigger}.
 *
 * @author nsakharo@google.com (Nick Sakharov)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class CallbackTrigger {
    private static final Logger LOG = Logger.getLogger(CallbackTrigger.class.getName());

    private final Runnable trigger;

    private final ImmutableSet<CallbackMetric<?, ?>> metrics;

    private volatile CallbackScope scope = null;

    /**
     * Must not be called directly - use {@link CallbackMetric#createTrigger} or
     * {@link MetricFactory#newTrigger} instead.
     */
    CallbackTrigger(Runnable trigger, Set<? extends CallbackMetric<?, ?>> metrics) {
        Preconditions.checkArgument(!metrics.isEmpty(), "Expected at least one metric");
        this.trigger = Preconditions.checkNotNull(trigger);
        this.metrics = ImmutableSet.copyOf(metrics);
    }

    void setScope(CallbackScope scope) {
        this.scope = scope;
    }

    CallbackScope getScope() {
        return scope;
    }

    Set<CallbackMetric<?, ?>> getMetrics() {
        return metrics;
    }

    /**
     * Method recursively populates provided trigger and metric sets based on the
     * known relationships between CallbackMetric and CallbackTrigger instances,
     * using current CallbackTrigger instance as a starting point. Once populated,
     * such sets would represent connected mesh of triggers and metrics that must
     * be updated in the consistent fashion.
     */
    void populateScopeMembers(Set<CallbackTrigger> triggerSet,
                              Set<CallbackMetric<?, ?>> metricSet) {
        if (triggerSet.add(this)) {
            for (CallbackMetric<?, ?> metric : metrics) {
                if (metricSet.add(metric)) {
                    for (CallbackTrigger trigger : metric.getTriggers()) {
                        trigger.populateScopeMembers(triggerSet, metricSet);
                    }
                }
            }
        }
    }

    void execute(CallbackScope.ExecutionState state) {
        try {
            state.setAllowedMetrics(metrics);
            // TODO(nsakharo): This may block indefinitely. Limit execution time.
            trigger.run();
        } catch (RuntimeException e) {
            // Log and supress all runtime exceptions from triggers.
            LOG.log(Level.WARNING, "Unexpected runtime exception during callback trigger execution.", e);
        } finally {
            state.setAllowedMetrics(ImmutableSet.<CallbackMetric<?, ?>>of());
        }
    }

    /**
     * Registers trigger, activating it. Must not be called directly - use
     * {@link CallbackMetric#createTrigger} or {@link MetricFactory#newTrigger} instead.
     */
    void register() {
        CallbackScope.registerTrigger(this);
        Preconditions.checkState(scope != null); // should never occur
    }

    /**
     * Deregisters this {@code CallbackTrigger}. After calling this method, this object
     * will no longer be used and can be discarded.
     */
    public void deregister() {
        CallbackScope.deregisterTrigger(this);
    }

    public boolean isRegistered() {
        return scope != null;
    }
}