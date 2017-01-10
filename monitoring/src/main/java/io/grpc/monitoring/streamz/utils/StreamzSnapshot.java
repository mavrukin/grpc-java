package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.monitoring.streamz.Distribution;
import io.grpc.monitoring.streamz.proto.MetricDefinition;
import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.StreamDefinition;
import io.grpc.monitoring.streamz.proto.StreamValue;
import io.grpc.monitoring.streamz.proto.TypedMessage;
import io.grpc.monitoring.streamz.proto.Types.FieldType;
import io.grpc.monitoring.streamz.proto.Types.ValueTypeDescriptor;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents the result of a streamz query, converting all
 * values into java objects indexed by metric name and fields.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class StreamzSnapshot {

  private final Map<String, MetricDefinition> metricDefinitionsByName = Maps.newHashMap();
  // invariant: values in map for a metric will always be of the type returned
  // by getValueType(metricName).
  private final Map<String, Map<FieldKey, Object>> values = Maps.newHashMap();
  private final Table<String, FieldKey, Long> timestamps = HashBasedTable.create();
  private final Table<String, FieldKey, Long> cellResetTimestamps = HashBasedTable.create();

  StreamzSnapshot(MonitorEvent data) {
    Map<Integer, MetricDefinition> metricDefinitions = Maps.newHashMap();

    for (StreamValue value : data.getValueList()) {
      readStream(metricDefinitions, value);
    }
  }

  private void readStream(Map<Integer, MetricDefinition> metricDefinitions, StreamValue value) {
    // TODO(ecurran): introduce some kind of client context to store these
    // things, e.g. in RemoteMetricReader. For now, we always use a fresh
    // context for each scrape in MetricReader, so it's ok.
    Preconditions.checkArgument(value.hasStreamDefinition(), "Data contained stream without a definition.");
    StreamDefinition streamDefinition = value.getStreamDefinition();
    MetricDefinition metricDefinition;

    if (streamDefinition.hasMetricDefinition()) {
      metricDefinition = streamDefinition.getMetricDefinition();
      metricDefinitions.put(streamDefinition.getMetricIndex(), metricDefinition);
      metricDefinitionsByName.put(metricDefinition.getName(), metricDefinition);
    } else {
      metricDefinition = metricDefinitions.get(streamDefinition.getMetricIndex());
    }

    FieldKey fieldKey = getFieldKey(metricDefinition, streamDefinition);
    Object valueObject = getValueObject(metricDefinition, value);
    Class<?> type = getValueType(metricDefinition);

    // Enums are special-cased, since their values are Strings.
    if (type.isAssignableFrom(Enum.class)) {
      type = String.class;
    }
    Preconditions.checkArgument(type == Void.class || type.isInstance(valueObject),
        "BUG in streamz/testing: Mis-matched type conversion for %s ", metricDefinition);

    Map<FieldKey, Object> metricMap = values.get(metricDefinition.getName());
    if (metricMap == null) {
      metricMap = Maps.newHashMap();
      values.put(metricDefinition.getName(), metricMap);
    }
    metricMap.put(fieldKey, valueObject);

    timestamps.put(metricDefinition.getName(), fieldKey, value.getTimestamp());

    cellResetTimestamps.put(
        metricDefinition.getName(), fieldKey, streamDefinition.getResetTimestamp());
  }

  /**
   * Converts a stream value to a java object, which will be of the type returned by
   * {@link #getValueType(MetricDefinition)}.
   */
  private static Object getValueObject(MetricDefinition metricDefinition, StreamValue value) {
    ValueTypeDescriptor valueTypeDescriptor = metricDefinition.getTypeDescriptor();
    switch (valueTypeDescriptor.getValueType()) {
      case VALUETYPE_BOOL:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.BOOL_VALUE_FIELD_NUMBER, "%s !value.hasBoolValue",
            metricDefinition.getName());
        return value.getBoolValue();
      case VALUETYPE_DISTRIBUTION:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.DISTRIBUTION_VALUE_FIELD_NUMBER, "%s !value.hasDistributionValue",
            metricDefinition.getName());
        try {
          return Distribution.fromProto(value.getDistributionValue());
        } catch (InvalidProtocolBufferException e) {
          throw new IllegalArgumentException(e);
        }
      case VALUETYPE_DOUBLE:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.DOUBLE_VALUE_FIELD_NUMBER, "%s !value.hasDoubleValue",
            metricDefinition.getName());
        return value.getDoubleValue();
      case VALUETYPE_EMPTY:
        return null;
      case VALUETYPE_ENUM:
        return value.getStringValue().toStringUtf8();
      case VALUETYPE_INT64:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.INT64_VALUE_FIELD_NUMBER, "%s !value.hasInt64Value",
            metricDefinition.getName());
        return value.getInt64Value();
      case VALUETYPE_MESSAGE:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.MESSAGE_VALUE_FIELD_NUMBER, "%s !value.hasMessageValue",
            metricDefinition.getName());
        return value.getMessageValue();
      case VALUETYPE_STRING:
        Preconditions.checkArgument(value.getValueCase().getNumber() == StreamValue.STRING_VALUE_FIELD_NUMBER, "%s !value.hasStringValue",
            metricDefinition.getName());
        return value.getStringValue().toStringUtf8();
      case VALUETYPE_VOID:
        return null;
      default:
        throw new IllegalArgumentException(
            "Support for valuetype unimplemented: " + valueTypeDescriptor);
    }
  }

  /**
   * Converts a StreamDefinition to a FieldKey, which will be used to store
   * metric values in the snapshot.
   */
  private static FieldKey getFieldKey(
      MetricDefinition metricDefinition, StreamDefinition streamDefinition) {
    Object[] fields = new Object[metricDefinition.getFieldTypeCount()];
    int stringIndex = 0;
    int intIndex = 0;
    int boolIndex = 0;
    for (int ii = 0; ii < metricDefinition.getFieldTypeCount(); ii++) {
      FieldType type = metricDefinition.getFieldType(ii);
      switch (type) {
        case FIELDTYPE_STRING:
          fields[ii] = streamDefinition.getStringFieldValues(stringIndex++);
          break;
        case FIELDTYPE_INT:
          fields[ii] = streamDefinition.getIntFieldValues(intIndex++);
          break;
        case FIELDTYPE_BOOL:
          fields[ii] = streamDefinition.getBoolFieldValues(boolIndex++);
          break;
        default:
          throw new IllegalArgumentException("Unknown type " + type);
      }
    }
    return FieldKey.of(fields);
  }

  /**
   * Gets the value of a cell, or {@code null} if that cell is not set,
   * checking the type of the value.
   *
   * <p>Note that when the metric holds enum value types, use {@code String.class}
   * instead of {@code Enum.class}.
   *
   * @throws IllegalStateException If the type does not match that given
   *     in the metric definition.
   */
  public <V> V getCellValue(String metricName, Class<V> valueType, FieldKey fieldKey) {
    if (!metricDefined(metricName)) {
      return null;
    }
    checkMetricValueType(metricName, valueType);
    @SuppressWarnings("unchecked")
    V downcastValue = (V) getCellValue(metricName, fieldKey);
    return downcastValue;
  }

  /**
   * Tells whether the given metric is defined in the snapshot.
   */
  boolean metricDefined(String metricName) {
    return metricDefinitionsByName.containsKey(metricName);
  }

  /**
   * Gets the value of a cell, or {@code null} if that cell is not set.
   * If not-null, the type of the returned object will be of type
   * {@link #getValueType(String)}, and {@code String} for {@code Enum}s.
   */
  public Object getCellValue(String metricName, FieldKey fieldKey) {
    Map<FieldKey, Object> metricMap = values.get(metricName);
    if (metricMap == null) {
      return null;
    }

    MetricDefinition definition = metricDefinitionsByName.get(metricName);
    Preconditions.checkArgument(fieldKey.size() == definition.getFieldTypeCount(),
        "Metric %s has %s fields but %s were supplied.", metricName,
        definition.getFieldTypeCount(), fieldKey.size());
    // TODO(ecurran): check that the field types match the definition?

    Object value = metricMap.get(fieldKey);
    if (value == null) {
      return null;
    }
    return value;
  }

  /**
   * Gets all values of a metric, or {@code null} if that metric has
   * no values set.
   * If not-null, the type of the returned objects will be of type
   * {@link #getValueType(String)}, and {@code String} for {@code Enum}s.
   */
  public Map<FieldKey, Object> getAllMetricValues(String metricName) {
    return values.get(metricName);
  }

  /**
   * Gets all values of a metric, or {@code null} if that metric has
   * no values set.
   * @throws IllegalStateException If the type does not match that given
   *     in the metric definition.
   */
  public <V> Map<FieldKey, V> getAllMetricValues(String metricName, Class<V> valueType) {
    if (!metricDefined(metricName)) {
      return null;
    }
    checkMetricValueType(metricName, valueType);
    @SuppressWarnings("unchecked")
    Map<FieldKey, V> result = (Map<FieldKey, V>) values.get(metricName);
    return result;
  }

  /**
   * Returns the type of values that will be returned for any values of the
   * given metric. If the metric had no values in the MonitorEvent data, then
   * returns null.
   *
   * <p>Note that this returns {@code java.lang.Enum} for all enum types,
   * so any original type information is lost.
   */
  public Class<?> getValueType(String metricName) {
    MetricDefinition definition = metricDefinitionsByName.get(metricName);
    return getValueType(definition);
  }

  /**
   * Returns the type of java object used to store the values of the given
   * metric.
   */
  private static Class<?> getValueType(MetricDefinition definition) {
    if (definition == null) {
      return null;
    }
    switch (definition.getTypeDescriptor().getValueType()) {
      case VALUETYPE_INT64: return Long.class;
      case VALUETYPE_EMPTY: return Void.class;
      case VALUETYPE_ENUM: return Enum.class;
      case VALUETYPE_MESSAGE: return TypedMessage.class;
      case VALUETYPE_STRING: return String.class;
      case VALUETYPE_BOOL: return Boolean.class;
      case VALUETYPE_DISTRIBUTION: return Distribution.class;
      case VALUETYPE_DOUBLE: return Double.class;
      case VALUETYPE_VOID: return Void.class;
      default: throw new IllegalStateException(
          "Unknown value type: " +
              definition.getTypeDescriptor().getValueType());
    }
  }

  /**
   * Checks that the specified metric has values of the given type.
   * If the metric did not appear in the snapshot, then the check
   * succeeds.
   * @param valueType The type of values which will be returned by
   *     {@link #getCellValue(String, FieldKey)}.
   * @throws IllegalStateException If the type does not match that given
   *     in the metric definition.
   */
  void checkMetricValueType(String metricName, Class<?> valueType) {
    Class<?> metricType = getValueType(metricName);
    Preconditions.checkNotNull(metricType, "Metric [%s] not defined", metricName);

    // short-circuit since enums cannot be returned as values.
    if (valueType == String.class && metricType == Enum.class) {
      return;
    }
    Preconditions.checkState(metricType.equals(valueType), "Metric [%s]: expected type %s but was %s", metricName, valueType, metricType);
  }

  /**
   * Return the metric definition for the supplied metric.
   */
  public MetricDefinition getMetricDefinition(String metricName) {
    return metricDefinitionsByName.get(metricName);
  }

  public Long getTimestamp(String metricName, FieldKey fieldKey) {
    return timestamps.get(metricName, fieldKey);
  }

  public Map<FieldKey, Long> getAllTimestamps(String metricName) {
    return timestamps.row(metricName);
  }

  public Long getResetTimestamp(String metricName, FieldKey fieldKey) {
    return cellResetTimestamps.get(metricName, fieldKey);
  }

  public Map<FieldKey, Long> getAllResetTimestamps(String metricName) {
    return cellResetTimestamps.row(metricName);
  }

  /**
   * Returns the names of all the metrics that appears in this snapshot
   */
  public Set<String> getAllMetricNames() {
    return ImmutableSet.copyOf(values.keySet());
  }

  @Override
  public String toString() {
    final StringBuilder out = new StringBuilder();
    final Set<String> metrics = new TreeSet<String>(getAllMetricNames());

    out.append("StreamzSnapshot{");
    for (final String metric : metrics) {
      out.append("\t");
      out.append(metric);
      out.append(": ");
      out.append(getAllMetricValues(metric));
      out.append("\n");
    }
    out.append("}");

    return out.toString();
  }
}