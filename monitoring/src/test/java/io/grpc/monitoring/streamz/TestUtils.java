package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.monitoring.streamz.proto.MetricAnnotation;
import io.grpc.monitoring.streamz.proto.MetricDefinition;
import io.grpc.monitoring.streamz.proto.StreamDefinition;
import io.grpc.monitoring.streamz.proto.StreamValue;
import io.grpc.monitoring.streamz.proto.Types.EncodedValueType;
import io.grpc.monitoring.streamz.proto.Types.ValueTypeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import junit.framework.ComparisonFailure;

/**
 * Utilities for testing streamz. Mostly used to allow access to package methods
 * on various streamz classes. Also used to allow code reuse when nearly-identical
 * test code needs to be applied in different unit tests.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class TestUtils {
  private static final long TEST_RESET_TIMESTAMP = 1234567L;
  private static final long TEST_TIMESTAMP = 123456789L;

  private TestUtils() {
  }

  /**
   * Returns reference to the metric with the given name. Metric must exist.
   *
   * <p>For {@code CallbackMetric}, this method will also result in trigger invocation,
   * updating metric values.
   */
  public static GenericMetric<?, ?> getMetric(MetricFactory metricFactory, String metricName) {
    // (Production code shouldn't use this  trick of taking Metrics outside the callback.)
    final GenericMetric<?, ?>[] r = { null };

    metricFactory.applyToMetric(metricName,
        new Receiver<GenericMetric<Object, ?>>() {
          @Override
          public void accept(GenericMetric<Object, ?> metric) {
            r[0] = metric;
          }
        });

    Preconditions.checkNotNull(r[0], "Metric '%s' does not exist.", metricName);
    return r[0];
  }

  /**
   * Fires triggers (if any) for all provided metric names.
   */
  public static void fireTriggers(MetricFactory metricFactory, String... metricNames) {
    metricFactory.applyToMetrics(ImmutableList.copyOf(metricNames),
        new Receiver<GenericMetric<Object, ?>>() {
          @Override
          public void accept(GenericMetric<Object, ?> metric) {}
        });
  }

  /**
   * Returns map of metrics and associated values for all metrics with the given directory. Expects
   * at most one value for each metric. Metrics with no values will be ignored and not added to the
   * final map.
   *
   * <p>For {@code CallbackMetric}, this method will also result in trigger invocation, updating
   * metric values.
   */
  public static Map<GenericMetric<Object, ?>, Object> getSingleValuesForMetrics(
      MetricFactory metricFactory, String directory) {
    final Map<GenericMetric<Object, ?>, Object> result = Maps.newHashMap();

    metricFactory.applyToMetricsBeneath(directory,
        new Receiver<GenericMetric<Object, ?>>() {
          @Override
          public void accept(GenericMetric<Object, ?> metric) {
            final List<Object> values = Lists.newArrayList();
            metric.applyToEachCell(new Receiver<GenericCell<Object>>() {
              @Override
              public void accept(@Nullable GenericCell<Object> genericCell) {
                values.add(genericCell.getValue());
              }
            });
            Preconditions.checkState(
                values.size() <= 1, "Expected at most 1 value from %s but got %s",
                metric.getName(), values.size());
            if (values.size() > 0) {
              result.put(metric, values.get(0));
            }
          }
        });

    return result;
  }

  /**
   * Returns current value of the given metric. Expects exactly one value.
   *
   * <p>Method will NOT invoke any triggers associated with the metric.
   */
  public static <V> V getSingleValue(GenericMetric<V, ?> m) {
    final List<V> results = Lists.newArrayList();
    m.applyToEachCell(new Receiver<GenericCell<V>>() {
      @Override
      public void accept(@Nullable GenericCell<V> genericCell) {
        results.add(genericCell.getValue());
      }
    });
    Preconditions.checkState(results.size() == 1, "Expected 1 value from %s but got %s",
        m.getName(), results.size());
    return results.get(0);
  }

  /**
   * Returns map of current values for the given one-dimensional metric.
   *
   * <p>Method will NOT invoke any triggers associated with the metric.
   */
  public static <V> Map<Object, V> getAllValuesOneDimensional(GenericMetric<V, ?> m) {
    Preconditions.checkArgument(m.getNumFields() == 1);
    final Map<Object, V> results = Maps.newHashMap();
    m.applyToEachCell(new Receiver<GenericCell<V>>() {
      @Override
      public void accept(@Nullable GenericCell<V> genericCell) {
        results.put(genericCell.getField(0), genericCell.getValue());
      }
    });
    return results;
  }

  /**
   * Returns map of current values for a given multi-dimensional metric.
   *
   * <p>Method will NOT invoke any triggers associated with the metric.
   */
  public static <V> Map<List<Object>, V> getAllValuesMultiDimensional(GenericMetric<V, ?> m) {
    final Map<List<Object>, V> results = Maps.newHashMap();
    final int size = m.getNumFields();
    m.applyToEachCell(new Receiver<GenericCell<V>>() {
      @Override
      public void accept(@Nullable GenericCell<V> genericCell) {
        List<Object> fields = new ArrayList<Object>();
        for (int i = 0; i < size; i++) {
          fields.add(genericCell.getField(i));
        }
        results.put(fields, genericCell.getValue());
      }
    });
    return results;
  }

  /**
   * Returns a simple StreamValue with Long data.
   *
   * @param index The metric index within the stream.
   */
  public static StreamValue createLongStreamValue(int index) {
    return StreamValue.newBuilder()
        .setStreamDefinition(StreamDefinition.newBuilder()
            .setMetricDefinition(MetricDefinition.newBuilder()
                .setName("test.google.com/myapp/long")
                .addAnnotation(MetricAnnotation.newBuilder()
                    .setName("DESCRIPTION")
                    .setValue("A long value"))
                .setTypeDescriptor(ValueTypeDescriptor.newBuilder()
                    .setValueType(EncodedValueType.VALUETYPE_INT64)))
            .setMetricIndex(index)
            .setResetTimestamp(TEST_RESET_TIMESTAMP))
        .setStreamIndex(index)
        .setInt64Value(123L)
        .setTimestamp(TEST_TIMESTAMP)
        .build();
  }

  /**
   * Returns a simple StreamValue with Counter data.
   *
   * @param index The metric index within the stream.
   */
  public static StreamValue createCounterStreamValue(int index) {
    return StreamValue.newBuilder()
        .setStreamDefinition(StreamDefinition.newBuilder()
            .setMetricDefinition(MetricDefinition.newBuilder()
                .setName("test.google.com/myapp/counter")
                .addAnnotation(MetricAnnotation.newBuilder()
                    .setName("DESCRIPTION")
                    .setValue("A counter"))
                .addAnnotation(MetricAnnotation.newBuilder()
                    .setName("CUMULATIVE")
                    .setValue("true"))
                .setTypeDescriptor(ValueTypeDescriptor.newBuilder()
                    .setValueType(EncodedValueType.VALUETYPE_INT64)))
            .setMetricIndex(index)
            .setResetTimestamp(TEST_RESET_TIMESTAMP))
        .setStreamIndex(index)
        .setInt64Value(234L)
        .setTimestamp(TEST_TIMESTAMP)
        .build();
  }

  /**
   * Eclipse and IntelliJ will show {@link ComparisonFailure} in a nice diff viewer.
   */
  public static void assertIdeFriendly(Object actual, Object expected)
      throws ComparisonFailure {
    if (!expected.equals(actual)) {
      throw new ComparisonFailure(
          "Comparison failed", expected.toString(), actual.toString());
    }
  }

  /**
   * Helper method to quickly produce and populate test metrics from the given
   * {@link MetricFactory}.
   */
  public static CallbackMetric0<Long> makeTestMetric(MetricFactory metricFactory,
      String name, String comment, final long value, boolean cumulative) {
    Metadata metadata = new Metadata(comment);
    if (cumulative) {
      metadata.setCumulative();
    }
    final CallbackMetric0<Long> longMetric = metricFactory.newCallbackMetric(
        name, Long.class, metadata);
    longMetric.createTrigger(new Runnable() {
      @Override public void run() {
        longMetric.set(value, TEST_TIMESTAMP, TEST_RESET_TIMESTAMP);
      }
    });
    return longMetric;
  }

  /**
   * Adds and exercises a long metric and a counter metric using the given factory.
   */
  public static void addManualCollectionTestMetrics(MetricFactory metricFactory) {
    // Throw in some metrics with a deterministic timestamp and reset timestamp.
    makeTestMetric(metricFactory, "test.google.com/myapp/long", "A long value", 123L, false);
    makeTestMetric(metricFactory, "test.google.com/myapp/counter", "A counter", 234L, true);
  }

  static CountingRunnable countingDecorator(Runnable delegate) {
    return new CountingRunnable(delegate);
  }

  static class CountingRunnable implements Runnable {
    private final AtomicInteger count = new AtomicInteger();
    private final Runnable delegate;

    private CountingRunnable(Runnable delegate) {
      this.delegate = delegate;
    }

    public int getRunCount() {
      return count.get();
    }

    public boolean wasCalled() {
      return getRunCount() > 0;
    }

    @Override
    public void run() {
      count.incrementAndGet();
      delegate.run();
    }
  }
}