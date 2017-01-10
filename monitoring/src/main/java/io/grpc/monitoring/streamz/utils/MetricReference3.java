// Generated by generate.py

package io.grpc.monitoring.streamz.utils;

import io.grpc.monitoring.streamz.Field;

import javax.annotation.Generated;

/**
 * Reference to an three-dimensional Metric. Used for testing.
 *
 * <p>See {@link StreamzTester} for factory methods.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @param <F3> The type of the third metric field.
 * @param <V> The type of the values stored in metric cells.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
*/
@Generated(value = "generate.py")
public class MetricReference3<F1, F2, F3, V>
     extends MetricReference<V> {

  MetricReference3(String metricName, Class<V> valueType,
      Field<F1> field1,
      Field<F2> field2,
      Field<F3> field3) {
    super(metricName, valueType, field1,
            field2,
            field3);
  }

  MetricReference3(String metricName, Class<V> valueType,
      String... fieldNames) {
    super(metricName, valueType, fieldNames);
  }

  public CellReference<V> cellReference(
      F1 field1,
      F2 field2,
      F3 field3) {
    return cellReference(createFieldKey(
        field1, field2, field3));
  }
}