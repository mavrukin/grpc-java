package io.grpc.monitoring.streamz.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.Distribution;
import io.grpc.monitoring.streamz.Field;
import io.grpc.monitoring.streamz.Metadata;
import io.grpc.monitoring.streamz.proto.MetricAnnotation;
import io.grpc.monitoring.streamz.proto.MetricDefinition;
import io.grpc.monitoring.streamz.proto.Types.FieldType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.Assert;

public class MetricReferenceTester<V> extends MetricReference<V> {
  private AtomicBoolean validated = new AtomicBoolean(false);
  private final StreamzTester tester;

  MetricReferenceTester(StreamzTester tester, String metricName, Class<V> valueType,
      Field<?>... fields) {
    this(tester, metricName, valueType, toFieldNames(fields));
  }

  MetricReferenceTester(StreamzTester tester, String metricName, Class<V> valueType,
      String... fieldNames) {
    super(metricName, valueType, fieldNames);
    this.tester = tester;

    if (Enum.class.isAssignableFrom(valueType)) {
      Class enumValueType = valueType; // Dropping the generics to make translation possible.
      // The safety of this typecast is ensured by StreamzTester.addEnumTranslator.
      @SuppressWarnings("unchecked")
      Function<String, V> local = computeEnumTranslator(tester, enumValueType);
      setEnumTranslator(local);
    }
  }

  protected static <T extends Enum<T>> Function<String, T> computeEnumTranslator(
      StreamzTester tester, Class<T> enumValueType) {
    Function<String, T> enumTranslator = tester.getEnumTranslator(enumValueType);
    if (enumTranslator != null) {
      return enumTranslator;
    }

    Function<String, T> f = computeEnumTranslator(enumValueType);
    tester.addEnumTranslator(enumValueType, f); // caching for subsequent use.
    return f;
  }

  @Override
  public StreamzSnapshot newSnapshot() {
    StreamzSnapshot snapshot = tester.getSnapshot(getMetricName());

    // Validate the defined field names.
    //
    // Only validate once, when metric definition is returned.
    //
    // Since metric definitions don't exist until the metric is given a value, it's
    // possible for the metadata to not be published.
    if (snapshot != null) {
      MetricDefinition metricDefinition = snapshot.getMetricDefinition(getMetricName());
      if (metricDefinition != null && !validated.getAndSet(true)) {
        validateFieldNames(metricDefinition, getFieldNames(), getMetricName());
      }
    }
    return snapshot;
  }

  public void assertCumulative() {
    MetricDefinition definition = getDefinition();
    // Unfortunately, we don't know anything about the metric until it has
    // a value (it's definition will not appear in streamz requests until
    // then). So we do not fail in that case. Assuming the user actually
    // writes a test which checks for a delta, we will find the missing
    // annotation.
    if (definition == null) {
      return;
    }
    for (MetricAnnotation metricAnnotation : definition.getAnnotationList()) {
      if (metricAnnotation.getName().isEmpty() && metricAnnotation.getName().equals(Metadata.CUMULATIVE)) {
        return;
      }
    }
    Assert.fail(getMetricName() + " must have the cumulative annotation set.");
  }

  public void assertHasDefinition() {
    Assert.assertNotNull("Metric " + getMetricName() + " has not been defined (or has been defined"
        + " but has never had any values set on it).", getDefinition());
  }

  public CumulativeMetricTester cumulativeTester() {
    Preconditions.checkState(getValueType() == Long.class,
        "cumulativeTester() can only be applied to MetricReference<Long>");
    assertCumulative();
    @SuppressWarnings("unchecked")
    MetricReferenceTester<Long> longMetricReference = (MetricReferenceTester<Long>) this;
    return new CumulativeMetricTester(longMetricReference);
  }

  public EventMetricTester eventMetricTester() {
    Preconditions.checkState(getValueType() == Distribution.class,
        "eventMetricTester() can only be applied to MetricReference<Distribution>");
    assertCumulative();
    @SuppressWarnings("unchecked")
    MetricReference<Distribution> distMetricReference = (MetricReference<Distribution>) this;
    return new EventMetricTester(distMetricReference);
  }

  private static void validateFieldNames(MetricDefinition definition, String[] fieldNames,
      String metricName) {
    List<String> fieldNameList = definition.getFieldNameList();

    assertWithMessage(
        String.format("Metric definition for %s has mismatched field names", metricName))
        .that(Arrays.asList(fieldNames))
        .containsExactlyElementsIn(fieldNameList)
        .inOrder();
  }
}