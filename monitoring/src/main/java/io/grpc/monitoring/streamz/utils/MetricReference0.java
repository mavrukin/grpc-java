package io.grpc.monitoring.streamz.utils;

/**
 * Reference to a zero-dimensional Metric.
 *
 * @param <V> The type of the values stored in metric cells.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class MetricReference0<V> extends MetricReference<V> {
  MetricReference0(String metricName, Class<V> valueType) {
    super(metricName, valueType, new String[]{});
  }

  public CellReference<V> cellReference() {
    return cellReference(createFieldKey());
  }
}