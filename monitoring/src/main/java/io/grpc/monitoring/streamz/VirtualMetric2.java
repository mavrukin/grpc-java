// Copyright 2010 Google Inc. All Rights Reserved.
// Generated by generate.py

package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import javax.annotation.Generated;

/**
 * A two-dimensional VirtualMetric.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @author ecurran@google.com (Eoin Curran)
 */
@Generated(value = "generate.py")
public class VirtualMetric2<F1, F2, V>
    extends VirtualMetric<V, VirtualMetric2<F1, F2, V>> {
  private final Receiver<DefineCellCallback<F1, F2, V>> definingFunction;

  /**
   * The callback that the defining function uses to define each
   * cell in the metric.
   */
  public interface DefineCellCallback<F1, F2, V> {

    /**
     * Defines the value of one cell in the metric.
     */
    public void defineCell(
        F1 field1,
      F2 field2,
        V value);
  }

  VirtualMetric2(
      String name, ValueTypeTraits<V> traits, Metadata metadata,
      ImmutableList<? extends Field<?>> fields,
      Receiver<DefineCellCallback<F1, F2, V>> definingFunction) {
    super(name, traits, metadata, fields);
    Preconditions.checkArgument(getNumFields() == 2);
    this.definingFunction = definingFunction;
  }

  @Override
  void applyToEachCell(final Receiver<GenericCell<V>> callback) {
    definingFunction.accept(new DefineCellCallback<F1, F2, V>() {
      @Override
      public void defineCell(
          F1 field1,
      F2 field2, V value) {
        callback.accept(new VirtualCell<V>(
            VirtualMetric2.this,
            new FieldTuple(field1, field2),
            value));
      }
    });
  }
}
