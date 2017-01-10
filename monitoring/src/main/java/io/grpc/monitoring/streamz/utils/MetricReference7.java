// Generated by generate.py

package io.grpc.monitoring.streamz.utils;

import io.grpc.monitoring.streamz.Field;

import javax.annotation.Generated;

/**
 * Reference to an seven-dimensional Metric. Used for testing.
 *
 * <p>See {@link StreamzTester} for factory methods.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @param <F3> The type of the third metric field.
 * @param <F4> The type of the fourth metric field.
 * @param <F5> The type of the fifth metric field.
 * @param <F6> The type of the sixth metric field.
 * @param <F7> The type of the seventh metric field.
 * @param <V> The type of the values stored in metric cells.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
*/
@Generated(value = "generate.py")
public class MetricReference7<F1, F2, F3, F4, F5, F6, F7, V>
     extends MetricReference<V> {

  MetricReference7(String metricName, Class<V> valueType,
      Field<F1> field1,
      Field<F2> field2,
      Field<F3> field3,
      Field<F4> field4,
      Field<F5> field5,
      Field<F6> field6,
      Field<F7> field7) {
    super(metricName, valueType, field1,
            field2,
            field3,
            field4,
            field5,
            field6,
            field7);
  }

  MetricReference7(String metricName, Class<V> valueType,
      String... fieldNames) {
    super(metricName, valueType, fieldNames);
  }

  public CellReference<V> cellReference(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6,
      F7 field7) {
    return cellReference(createFieldKey(
        field1, field2, field3, field4, field5, field6, field7));
  }
}