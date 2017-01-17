package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.proto.StreamValue.Builder;

/**
 * A cell in a {@link CallbackMetric}. Short-lived instances of this class are created
 * on-the-fly as the callback function defines them.
 *
 * @param <V> The type of data in the metric/cell.
 */
class CallbackCell<V> extends GenericCell<V> {
    protected final CellKey<CallbackMetric<V, ?>> cellKey;
    protected final V value;

    CallbackCell(CallbackMetric<V, ?> owner, FieldTuple tuple, V value) {
        this.cellKey = new CellKey<CallbackMetric<V, ?>>(owner, tuple);
        this.value = value;
    }

    @Override
    CellKey<?> getCellKey() {
        return cellKey;
    }

    @Override
    GenericMetric<V, ? extends GenericMetric<V, ?>> getOwner() {
        return cellKey.metric;
    }

    @Override
    long getResetTimestampMicros() {
        return cellKey.metric.getNewCellResetTimestampMicros();
    }

    @Override
    Object getField(int i) {
        return cellKey.tuple.get(i);
    }

    @Override
    V getValue() {
        return value;
    }

    @Override
    void toStreamValue(Builder streamValue, boolean includeTimestamp) {
        if (value != null) {
            cellKey.metric.getValueTypeTraits().toStreamValue(streamValue, value);
        }
    }

    static final class Timestamped<V> extends CallbackCell<V> {
        private final long timestampMicros;
        private final long resetTimestampMicros;

        Timestamped(
                CallbackMetric<V, ?> owner, FieldTuple tuple, V value,
                long timestampMicros, long resetTimestampMicros) {
            super(owner, tuple, value);
            this.timestampMicros = timestampMicros;
            this.resetTimestampMicros = resetTimestampMicros;
            validateTimestamps();
        }

        Timestamped(
                CallbackMetric<V, ?> owner, FieldTuple tuple, V value,
                long timestampMicros) {
            super(owner, tuple, value);
            this.timestampMicros = timestampMicros;
            this.resetTimestampMicros = cellKey.metric.getNewCellResetTimestampMicros();
            validateTimestamps();
        }

        @Override
        long getResetTimestampMicros() {
            return resetTimestampMicros;
        }

        @Override
        ValueAtTimestamp<V> getTimestampedValue() {
            return new ValueAtTimestamp<V>(timestampMicros, value);
        }

        @Override
        void toStreamValue(Builder streamValue, boolean includeTimestamp) {
            if (value != null) {
                cellKey.metric.getValueTypeTraits().toStreamValue(streamValue, value);
                if (includeTimestamp) {
                    streamValue.setTimestamp(timestampMicros);
                }
            }
        }

        private void validateTimestamps() {
            Preconditions.checkArgument(timestampMicros >= resetTimestampMicros,
                    "Timestamp must be higher or equal to reset timestamp, but %d < %d",
                    timestampMicros, resetTimestampMicros);
        }
    }
}