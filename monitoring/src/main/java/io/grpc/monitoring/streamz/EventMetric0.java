package io.grpc.monitoring.streamz;

import com.google.protobuf.Any;

/**
 * A zero-dimensional (single distribution) EventMetric.
 *
 * <p>See {@link MetricFactory} for factory methods.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class EventMetric0 extends EventMetricBase<EventMetric0> {
    private final Metric0<Distribution> metric;

    EventMetric0(String name, Bucketer bucketer, Metadata metadata) {
        super(bucketer);
        this.metric = new Metric0<Distribution>(
                name, ValueTypeTraits.getTraits(Distribution.class), metadata.setCumulative());
        metric.setDefaultValue(new Distribution(bucketer));
    }

    /**
     * Records the given value in the distribution.
     */
    public void record(final double value) {
        if (Distribution.isValidSample(value, 1)) {
            metric.changeUnderLock(createChanger(value));
        }
    }

    /**
     * Records the given value in the distribution with provided
     * com.google.protobuf.Any attachments. All provided attachments must
     * use different type URL.
     */
    public void recordWithExemplar(double value, Any... attachments) {
        if (Distribution.isValidSample(value, 1)) {
            metric.changeUnderLock(createChanger(value, attachments));
        }
    }

    /**
     * Records the given value in the distribution, "count" times.
     */
    public void recordMultiple(final double value, long count) {
        if (Distribution.isValidSample(value, count)) {
            metric.changeUnderLock(createChanger(value, count));
        }
    }

    /**
     * Ensures a distribution is defined for this metric by creating one with
     * size zero. If one already exists for this metric, there is no change.
     */
    public EventMetric0 forceCreate() {
        metric.getOrCreateCell(metric.createFieldTuple());
        return this;
    }

    /**
     * Returns a copy of the distribution.
     */
    public Distribution get() {
        return metric.get();
    }

    @Override
    protected StoredMetric<Distribution, ?> getUnderlyingMetric() {
        return metric;
    }
}