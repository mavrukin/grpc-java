package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A metric whose values are defined exclusively by provided {@code CallbackTrigger}.
 * Metric allows its values to be set only from inside provided Runnable instance,
 * which is triggered whenever system needs to collect metric values.
 *
 * <p>Trigger itself is provided by the {@link CallbackTrigger} instance. Both could only
 * be instantiated using {@link MetricFactory}. However, as a shortcut, trigger associated
 * with only single metric, could also be defined using {@link CallbackMetric#createTrigger}
 * method.
 *
 * <p>NB! Attempting to set metric values outside of the callback is considered to be an
 * illegal operation and will result in {@link IllegalStateException} being thrown.
 *
 * @see MetricFactory for factory methods.
 *
 * @param <V> The type of the metric value.
 * @param <M> The type of the concrete Metric subclass.
 * @author nsakharo@google.com (Nick Sakharov)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public abstract class CallbackMetric<V, M extends CallbackMetric<V, M>> extends GenericMetric<V, M> {
    private static final Logger logger = Logger.getLogger(CallbackMetric.class.getName());
    private Set<CallbackTrigger> triggers = Sets.newCopyOnWriteArraySet();

    private volatile CallbackScope.ExecutionState executionState = null;

    // map holds cell values while triggers are firing, cells holds the values for collection.
    private final Map<FieldTuple, CallbackCell<V>> map = Maps.newHashMap();
    private final Set<CallbackCell<V>> cells = Sets.newCopyOnWriteArraySet();

    CallbackMetric(
            String name, ValueTypeTraits<V> traits, Metadata metadata,
            ImmutableList<? extends Field<?>> fields) {
        super(name, traits, metadata, fields);
        // The reset timestamp should be initialized to metric creation time.
        getNewCellResetTimestampMicros();
    }

    @Override
    void applyToEachCell(Receiver<GenericCell<V>> callback) {
        CallbackScope.ExecutionState state = executionState; // defensive copy
        if (state != null) {
            try {
                state.waitIfUpdating();
                for (CallbackCell<V> cell : cells) {
                    callback.accept(cell);
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO,
                        "Update of metric values has been interrupted. Ignoring collected values.", e);
                Thread.currentThread().interrupt(); // restore interrupted status
            }
        }
    }

    void reset(CallbackScope.ExecutionState state) {
        executionState = state;
        map.clear();
    }

    void publishCells() {
        cells.clear();
        cells.addAll(map.values());
        map.clear();
    }

    private void verifySetPreconditions(FieldTuple tuple, V value) {
        CallbackScope.ExecutionState state = executionState; //defensive copy
        Preconditions.checkState(state != null && state.isMetricUpdatable(this),
                "Attempted to set callback metric value outside of the trigger execution.");
        verifyFieldTuple(tuple);
        // TODO(nsakharo): Code below effectively prevents set() being called twice for
        // the same cell during trigger execution (most common error I anticipate here is for
        // two separate triggers to update same cell value). Right now this is prohibited.
        // Perhaps we should relax this check and only log warning instead. However, do keep
        // in mind that trigger execution order is not guaranteed - thus user can't control
        // which set() call will be executed last.
        Preconditions.checkState(!map.containsKey(tuple),
                "Attempted to set callback metric cell value multiple times during trigger execution" +
                        " (possibly from multiple triggers).  Cell: %s", tuple);
    }

    void set(FieldTuple tuple, V value) {
        verifySetPreconditions(tuple, value);
        map.put(tuple, new CallbackCell<V>(this, tuple, value));
    }

    void set(FieldTuple tuple, V value, long timestampMicros) {
        verifySetPreconditions(tuple, value);
        map.put(tuple,
                new CallbackCell.Timestamped<V>(this, tuple, value, timestampMicros));
    }

    void set(FieldTuple tuple, V value, long timestampMicros, long resetTimestampMicros) {
        verifySetPreconditions(tuple, value);
        map.put(tuple,
                new CallbackCell.Timestamped<V>(this, tuple, value, timestampMicros, resetTimestampMicros));
    }

    FieldTuple createFieldTuple(Object... fields) {
        return new FieldTuple(fields);
    }

    /**
     * Creates a {@code CallbackTrigger} associated exclusively with this metric. Essentially,
     * it is a shortcut form of {@code MetricFactory.getDefault().newTrigger(trigger, metric)}.
     *
     * <p>This method provides syntactic sugar, allowing one to define a metric's trigger
     * together with the metric definition. E.g.,
     *
     * <pre>
     * class MyService {
     * ...
     * private final CallbackMetric0<Integer> outstandingRequestsMetric =
     *     MetricFactory.getDefault().newCallbackMetric(
     *     "/my/service/outstanding_requests",
     *     Integer.class,
     *     new Metadata("Outstanding request count"))
     *     .createTrigger(new Runnable() {
     *       @Override public void run() {
     *         outstandingRequestsMetric.set(this.getActiveRequestCount());
     *       }
     *     });
     * ...
     * }
     * </pre>
     *
     * <p>Note: Since method never returns the trigger reference it should be used only
     * for triggers that are active during whole metric lifetime. If trigger needs to be
     * deregistered at some point, {@link MetricFactory#newTrigger} should be used instead.
     *
     * @see CallbackTrigger
     *
     * @param trigger trigger's Runnable instance that will be executed each time
     *        when metric value needs to be updated
     * @return {@code CallbackMetric} reference
     */
    @SuppressWarnings("unchecked") // see comment below
    public M createTrigger(final Runnable trigger) {
        new CallbackTrigger(trigger, ImmutableSet.of(this)).register();
        // Compiler can't recognize the fact that "this" will always correspond to type M in
        // all CallbackMetricN subclasses - requiring us to resort to an explicit cast and
        // suppressed "unchecked" warning.
        return (M) this;
    }

    CallbackScope getScope() {
        // All triggers associated with the metric will belong to the same scope
        // by definition - so pick the first one to get scope reference.
        Iterator<CallbackTrigger> it = triggers.iterator();
        return it.hasNext() ? it.next().getScope() : null;
    }

    Set<CallbackTrigger> getTriggers() {
        return triggers;
    }

    void addTrigger(CallbackTrigger trigger) {
        Preconditions.checkNotNull(trigger);
        triggers.add(trigger);
    }

    void removeTrigger(CallbackTrigger trigger) {
        triggers.remove(trigger);
        reset(null); // drop all values since one of the triggers is no longer valid.
    }
}