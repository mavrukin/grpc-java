package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A zero-dimensional CallbackMetric.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <V> The type of the values stored in metric cells.
 * @author nsakharo@google.com (Nick Sakharov)
 */
public class CallbackMetric0<V> extends CallbackMetric<V, CallbackMetric0<V>> {
    CallbackMetric0(String name, ValueTypeTraits<V> traits, Metadata metadata) {
        super(name, traits, metadata, ImmutableList.<Field<?>>of());
        Preconditions.checkArgument(getNumFields() == 0);
    }

    /**
     * Sets the value of the given cell to {@code value}. Must be called only from the
     * {@link CallbackTrigger} instance associated with the metric - any calls outside of
     * the trigger context will be ignored (and will result in exception if strict checking
     * is enabled). Also, each cell should be set at most once - while subsequent set()
     * invocations for the same cell will succeed, they too will result in exception if strict
     * checking is enabled.
     *
     * <p>Also see {@link StoredCell#updateValue(Object)} for restrictions placed on the
     * {@code value}.
     *
     * @see StoredCell#updateValue(Object)
     */
    public void set(
            V value) {
        set(FieldTuple.NO_FIELDS, value);
    }

    /**
     * Sets the value of the given cell to {@code value}, while setting the timestamp to
     * {@code timestampMicros}.
     *
     * Must be called only from the {@link CallbackTrigger} instance associated with the
     * metric - any calls outside of the trigger context will be ignored (and will result in
     * an exception if strict checking is enabled). Also, each cell should be set at most
     * once - while subsequent set() invocations for the same cell will succeed, they too
     * will result in an exception if strict checking is enabled.
     *
     * Setting timestamp explicitly is an advanced feature: use {@code #set(V)} instead
     * unless it really is necessary to do so. If timestamp is set explicitly, then ensure
     * that it is after the time when the corresponding metric was created: monitoring systems are
     * likely to discard data if timestamps are out of order.
     *
     * <p>Also see {@link StoredCell#updateValue(Object)} for restrictions placed on the
     * {@code value}.
     *
     * @see StoredCell#updateValue(Object)
     */
    public void set(V value, long timestampMicros) {
        set(FieldTuple.NO_FIELDS, value, timestampMicros);
    }

    /**
     * Sets the value of the given cell to {@code value}, while setting the timestamp and
     * reset timestamp to {@code timestampMicros} and {@code resetTimestampMicros}, respectively.
     *
     * Must be called only from the {@link CallbackTrigger} instance associated with the
     * metric - any calls outside of the trigger context will be ignored (and will result in
     * an exception if strict checking is enabled). Also, each cell should be set at most
     * once - while subsequent set() invocations for the same cell will succeed, they too
     * will result in an exception if strict checking is enabled.
     *
     * Setting timestamps explicitly is an advanced feature: use {@code #set(V)} instead
     * unless it really is necessary to do so. If timestamps are set explicitly, then ensure
     * that timestamps and reset timestamps do not go backwards: monitoring systems are
     * likely to discard data if timestamps are out of order. It is an error for the reset
     * timestamp to be higher than the timestamp.
     *
     * <p>Also see {@link StoredCell#updateValue(Object)} for restrictions placed on the
     * {@code value}.
     *
     * @see StoredCell#updateValue(Object)
     */
    public void set(
            V value, long timestampMicros, long resetTimestampMicros) {
        set(FieldTuple.NO_FIELDS, value, timestampMicros, resetTimestampMicros);
    }
}