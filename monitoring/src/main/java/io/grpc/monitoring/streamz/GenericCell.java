package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;

import io.grpc.monitoring.streamz.proto.StreamValue;

/**
 * @param <V> The type of the metric value.
 * @author ecurran@google.com (Eoin Curran)
 */
abstract class GenericCell<V> {
    abstract GenericMetric<V, ? extends GenericMetric<V, ?>> getOwner();

    abstract Object getField(int i);

    abstract CellKey<?> getCellKey();

    /**
     * Populate the contents of {@code streamValue} with data from this cell.
     *
     * If a timestamp was not explicitly specified when setting a value, then
     * no timestamp is included even if {@code includeTimestamp} is true.
     */
    abstract void toStreamValue(StreamValue.Builder streamValue, boolean includeTimestamp);

    /**
     * Returns the reset timestamp of this cell.
     */
    abstract long getResetTimestampMicros();

    /**
     * Returns the value of this cell. The result is a copy of the value iff type
     * V is mutable (e.g. Distribution). The result may be null.
     */
    abstract V getValue();

    /**
     * Returns the timestamp and value of this cell. Will always return a
     * timestamp. The second entry of the result is a copy of the value iff
     * type V is mutable (e.g. Distribution), and may be null if the cell
     * has not been initialized.
     *
     * If the timestamp was not explicitly specified when the value was set,
     * then the returned timestamp will be the current time.  Unspecified
     * timestamps implicitly mean that the timestamp should be at time of
     * metric scrape to increase timestamp sharing, which reduces the amount
     * of data that has to be stored.
     */
    Pair<Long, V> getTimestampedValue() {
        return Pair.of(Utils.getCurrentTimeMicros(), getValue());
    }

    @Override
    public String toString() {
        GenericMetric<V, ? extends GenericMetric<V, ?>> owner = getOwner();
        if (owner.getNumFields() == 0) { return owner.getName(); }
        StringBuilder s = new StringBuilder(owner.getName());
        s.append('{');
        String sep = "";
        for (int ii = 0; ii < owner.getNumFields(); ii++) {
            s.append(sep).append(owner.getField(ii).getName()).append('=').append(getField(ii));
            sep = ", ";
        }
        s.append('}');
        return s.toString();
    }

    static final class CellKey<M> {
        final M metric;
        final FieldTuple tuple;
        private final int hashCode;

        CellKey(M metric, FieldTuple tuple) {
            this.metric = Preconditions.checkNotNull(metric);
            this.tuple = Preconditions.checkNotNull(tuple);
            this.hashCode = 31 * metric.hashCode() +  tuple.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }

            CellKey<?> cellKey = (CellKey<?>) o;

            return metric.equals(cellKey.metric) && tuple.equals(cellKey.tuple);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}