package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A zero-dimensional (single cell) metric.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <V> The type of the metric value.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class Metric0<V> extends StoredMetric<V, Metric0<V>> {
    Metric0(String name, ValueTypeTraits<V> traits, Metadata metadata) {
        super(name, traits, metadata, ImmutableList.<Field<?>>of());
        Preconditions.checkState(getNumFields() == 0);
    }

    /**
     * @see StoredCell#updateValue(Object)
     */
    public void set(V value) {
        set(FieldTuple.NO_FIELDS, value);
    }

    /**
     * @see StoredCell#getValue()
     */
    public V get() {
        return get(FieldTuple.NO_FIELDS);
    }

    /**
     * @see StoredCell#increment()
     */
    public void increment() {
        increment(FieldTuple.NO_FIELDS);
    }

    /**
     * @see StoredCell#decrement()
     */
    public void decrement() {
        decrement(FieldTuple.NO_FIELDS);
    }

    /**
     * @see StoredCell#incrementBy(Number)
     */
    public void incrementBy(Number value) {
        incrementBy(FieldTuple.NO_FIELDS, value);
    }

    /**
     * @see StoredCell#changeUnderLock(CellValueChanger)
     */
    public void changeUnderLock(CellValueChanger<V> changer) {
        changeUnderLock(FieldTuple.NO_FIELDS, changer);
    }

    @VisibleForTesting
    StoredCell<V> getCell() {
        return getOrCreateCell(FieldTuple.NO_FIELDS);
    }
}