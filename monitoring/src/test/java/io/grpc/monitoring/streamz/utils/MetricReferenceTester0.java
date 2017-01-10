package io.grpc.monitoring.streamz.utils;

/**
 * Reference to a zero-dimensional Metric. Used for testing.
 *
 * <p>See {@link StreamzTester} for factory methods.
 *
 * @param <V> The type of the values stored in metric cells.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class MetricReferenceTester0<V> extends MetricReferenceTester<V> {
  MetricReferenceTester0(StreamzTester tester, String metricName, Class<V> valueType) {
    super(tester, metricName, valueType, new String[]{});
  }
}