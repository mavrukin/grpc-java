package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * Zero-dimensional (single-valued) VirtualMetric, defined by
 * a simple {@link Supplier}.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class VirtualMetric0<V> extends VirtualMetric<V, VirtualMetric0<V>> {
    private final Supplier<V> supplier;

    VirtualMetric0(
            String name, ValueTypeTraits<V> traits, Metadata metadata, Supplier<V> supplier) {
        super(name, traits, metadata, ImmutableList.<Field<?>>of());
        Preconditions.checkArgument(this.getNumFields() == 0);
        this.supplier = supplier;
    }

    @Override
    void applyToEachCell(final Receiver<GenericCell<V>> callback) {
        callback.accept(new VirtualCell<V>(VirtualMetric0.this, FieldTuple.NO_FIELDS, supplier.get()));
    }
}