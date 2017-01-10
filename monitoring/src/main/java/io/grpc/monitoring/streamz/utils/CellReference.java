package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.Distribution;

/**
 * Access the value of a single cell.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class CellReference<V> {
  private final MetricReference<V> metric;
  private final FieldKey fieldKey;

  CellReference(MetricReference<V> metric, FieldKey fieldKey) {
    this.metric = Preconditions.checkNotNull(metric);
    this.fieldKey = Preconditions.checkNotNull(fieldKey);
  }

  public V getValue() {
    return getValue(metric.newSnapshot());
  }

  public V getValue(StreamzSnapshot streamzSnapshot) {
    Function<String, V> enumTranslator = metric.getEnumTranslator();
    if (enumTranslator == null) {
      return streamzSnapshot.getCellValue(metric.getMetricName(), metric.getValueType(), fieldKey);
    } else {
      // Enums with a translator get restored to their original value.
      return enumTranslator.apply(
          streamzSnapshot.getCellValue(metric.getMetricName(), String.class, fieldKey));
    }
  }

  protected MetricReference<V> getMetricReference() {
    return metric;
  }

  FieldKey getFieldKey() {
    return fieldKey;
  }
}