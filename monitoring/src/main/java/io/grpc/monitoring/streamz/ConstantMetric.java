package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * @param <V> The type of the metric value.
 * @author ecurran@google.com (Eoin Curran)
 */
public class ConstantMetric<V> extends StoredMetric<V, ConstantMetric<V>> {
    ConstantMetric(String name, Class<V> clazz, V value, Metadata metadata) {
        super(name, ValueTypeTraits.getTraits(clazz),
                metadata, ImmutableList.<Field<?>>of());
        Preconditions.checkState(getNumFields() == 0);
        set(FieldTuple.NO_FIELDS, value);
    }
}