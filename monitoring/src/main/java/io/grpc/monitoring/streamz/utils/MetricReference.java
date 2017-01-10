package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.grpc.monitoring.streamz.Distribution;
import io.grpc.monitoring.streamz.Field;
import io.grpc.monitoring.streamz.Metadata;
import io.grpc.monitoring.streamz.proto.MetricAnnotation;
import io.grpc.monitoring.streamz.proto.MetricDefinition;
import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.Types.FieldType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Access the values of a metric.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author konigsberg@google.com (Robert Konigsberg)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class MetricReference<V> {

  // Support for enum values.
  protected static final Method ENUM_TO_STRING_METHOD;
  static {
    try {
      ENUM_TO_STRING_METHOD = Enum.class.getMethod("toString");
    } catch (SecurityException e) {
      // These exceptions just aren't going to be thrown.
      throw new AssertionError(e);
    } catch (NoSuchMethodException e) {
      // These exceptions just aren't going to be thrown.
      throw new AssertionError(e);
    }
  }

  private final String metricName;
  private final Class<V> valueType;
  private final String[] fieldNames;
  private Function<String, V> enumTranslator;

  MetricReference(String metricName, Class<V> valueType,
      Field<?>... fields) {
    this(metricName, valueType, toFieldNames(fields));
  }

  protected static String[] toFieldNames(Field<?>[] fields) {
    String[] names = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      names[i] = fields[i].getName();
    }
    return names;
  }

  MetricReference(String metricName, Class<V> valueType,
      String... fieldNames) {
    Preconditions.checkArgument(
        valueType != Integer.class,
        "Ints are converted to longs, so Integer.class is invalid. Use Long.class.");
    this.metricName = metricName;
    this.valueType = valueType;
    this.fieldNames = fieldNames;

    if (!Enum.class.isAssignableFrom(valueType)) {
      this.enumTranslator = null;
    }
  }

  protected static <T extends Enum<T>> Function<String, T> computeEnumTranslator(
      Class<T> enumValueType) {
    // If a custom translator is not supplied and if the enum has not overridden toString,
    // it can be translated automatically.
    Method valueTypeToString;
    try {
      valueTypeToString = enumValueType.getMethod("toString");
    } catch (SecurityException e) {
      // This gets thrown when either the method is not public, so, not possible, and,
      // the security manager denies access to the class, which might be possible.
      // A more concrete RuntimeException would be preferred.
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      // every class has a zero-argument toString method.
      throw new AssertionError(e);
    }
    if (valueTypeToString.equals(ENUM_TO_STRING_METHOD)) {
      return Enums.stringConverter(enumValueType);
    }
    throw new IllegalArgumentException(
        "The enum type " + enumValueType.getName() + " overrides toString and does not have a " +
            "custom translator defined. Either use StreamzTester.addEnumTranslator or define your " +
            "MetricReference's value type as String. " +
            "(This is unrelated to enums used as field types.)");
  }

  public StreamzSnapshot newSnapshot() {
    return new StreamzSnapshot(MonitorEvent.getDefaultInstance());
  }

  MetricDefinition getDefinition() {
    return newSnapshot().getMetricDefinition(metricName);
  }

  /**
   * Get map of all keys and values stored for the metric referenced by this, using a
   * supplied {@link StreamzSnapshot}.
   */
  public Map<FieldKey, V> getValues(StreamzSnapshot streamzSnapshot) {
    Map<FieldKey, V> values = streamzSnapshot.getAllMetricValues(metricName, valueType);
    if (values == null) {
      return ImmutableMap.of();
    }
    return values;
  }

  /**
   * Get map of all keys and values stored for the metric referenced by this.
   */
  public Map<FieldKey, V> getValues() {
    return getValues(newSnapshot());
  }

  /**
   * Get the name of the metric referenced here.
   */
  public String getMetricName() {
    return metricName;
  }

  /**
   * Get the type of the metric referenced here.
   */
  public Class<V> getValueType() {
    return valueType;
  }

  public String[] getFieldNames() { return fieldNames; }

  CellReference<V> cellReference(FieldKey fieldKey) {
    return new CellReference<V>(this, fieldKey);
  }



  // Support the Field.asEnumOfString case. Oh man, this is a nasty hack.
  protected Object[] translateEnums(Object[] fieldValues) {
    Object[] arry = new Object[fieldValues.length];
    System.arraycopy(fieldValues, 0, arry, 0, fieldValues.length);
    for (int i = 0; i < arry.length; i++) {
      arry[i] = (fieldValues[i] instanceof Enum) ? fieldValues[i].toString() : fieldValues[i];
    }
    return arry;
  }


  Function<String, V> getEnumTranslator() {
    return enumTranslator;
  }

  void setEnumTranslator(Function<String, V> translator) {
    this.enumTranslator = translator;
  }

  FieldKey createFieldKey(Object... fieldValues) {
    fieldValues = translateEnums(fieldValues);
    MetricDefinition definition = getDefinition();

    if (definition != null) {
      // Only validate the field types when metric definition is returned.
      //
      // Since metric definitions don't exist until the metric is given a value, it's
      // possible for the metadata to not be published.
      validateFieldValueTypes(definition, fieldValues);
    }
    return FieldKey.of(fieldValues);
  }

  static void validateFieldValueTypes(MetricDefinition definition, Object... fieldValues) {
    List<FieldType> fieldTypeList = definition.getFieldTypeList();
    for (int i = 0; i < fieldValues.length; i++) {
      FieldType fieldType = fieldTypeList.get(i);
      Class<?> baseClass;
      switch(fieldType) {
        case FIELDTYPE_BOOL:
          baseClass = Boolean.class;
          break;
        case FIELDTYPE_INT:
          baseClass = Integer.class;
          break;
        case FIELDTYPE_STRING:
          baseClass = String.class;
          break;
        default:
          throw new IllegalStateException(String.format("Unknown field type from %s from %s",
              fieldType, fieldTypeList));
      }
      Class<? extends Object> classOfValue = fieldValues[i].getClass();
      Preconditions.checkState(classOfValue.isAssignableFrom(baseClass),
          "Field %d is of invalid type %s (fields are %s)", i, classOfValue, fieldTypeList);
    }
  }

}