package io.grpc.monitoring.streamz;

import io.grpc.monitoring.streamz.proto.StreamValue.Builder;

/**
 * A cell in a {@link VirtualMetric}. Short-lived instances of this class are created
 * on-the-fly as the callback function defines them.
 *
 * @param <V> The type of data in the metric/cell.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
final class VirtualCell<V> extends GenericCell<V> {
    private final CellKey<VirtualMetric<V, ?>> cellKey;
    private final V value;

    VirtualCell(VirtualMetric<V, ?> owner, FieldTuple tuple, V value) {
        this.cellKey = new CellKey<VirtualMetric<V, ?>>(owner, tuple);
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

    void toStreamValue(Builder streamValue, boolean includeTimestamp) {
        if (value != null) {
            cellKey.metric.getValueTypeTraits().toStreamValue(streamValue, value);
        }
        // If an explicit timestamp is ever stored, then streamValue.setTimestamp() should be
        // called with this timestamp when includeTimestamp is true.
    }
}