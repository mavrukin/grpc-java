// Generated by generate.py

package io.grpc.monitoring.streamz.utils;

import io.grpc.monitoring.streamz.Field;

import javax.annotation.Generated;

/**
 * Reference to an one-dimensional Metric. Used for testing.
 *
 * <p>See {@link StreamzTester} for factory methods.
 *
 * @param <F1> The type of the first metric field.
 * @param <V> The type of the values stored in metric cells.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
*/
@Generated(value = "generate.py")
public class MetricReferenceTester1<F1, V>
     extends MetricReferenceTester<V> {

  MetricReferenceTester1(StreamzTester tester, String metricName,
      Class<V> valueType,
      Field<F1> field1) {
    super(tester, metricName, valueType, field1);
  }

  MetricReferenceTester1(StreamzTester tester, String metricName, Class<V> valueType,
      String... fieldNames) {
    super(tester, metricName, valueType, fieldNames);
  }
}