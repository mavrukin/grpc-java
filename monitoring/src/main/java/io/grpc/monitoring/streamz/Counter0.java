package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A zero-dimensional Counter. This is essentially a Metric0<Long>,
 * and has a restricted API that only permits incrementing the metric.
 *
 * <p>See {@link MetricFactory} for factory methods.
 *
 * @author konigsberg@google.com (Robert Konigsberg)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class Counter0 extends StoredMetric<Long, Counter0> {

    Counter0(String name, ValueTypeTraits<Long> traits, Metadata metadata) {
        super(name, traits, metadata, ImmutableList.<Field<?>>of());
        Preconditions.checkState(getNumFields() == 0);
    }

    /**
     * @see StoredCell#increment()
     */
    public void increment() {
        increment(FieldTuple.NO_FIELDS);
    }

    /**
     * @see StoredCell#incrementBy(Number)
     */
    public void incrementBy(long value) {
        incrementBy(FieldTuple.NO_FIELDS, value);
    }

    /**
     * @see StoredCell#getValue()
     */
    public Long get() {
        return get(FieldTuple.NO_FIELDS);
    }
}