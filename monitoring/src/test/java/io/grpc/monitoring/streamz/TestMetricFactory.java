package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

/**
 * Metric factory for use in tests.  Provides protection from registering
 * multiple metrics of the same name, which is not allowed for exported metrics.
 *
 * @author flooey@google.com (Adam Vartanian)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public final class TestMetricFactory extends MetricFactory {
  private final MetricRegistry delegate;

  @Inject
  public TestMetricFactory() {
    this.delegate = new MetricRegistry();
  }

  @VisibleForTesting
  TestMetricFactory(MetricRegistry delegate) {
    this.delegate = delegate;
  }

  @Override
  protected void onCreate(GenericMetric<?, ?> metric) {
    delegate.add(metric);
  }

  /**
   * Returns whether a metric of the given name was created by this factory.
   */
  public boolean metricExists(String name) {
    return delegate.metricExists(name);
  }

  @Override
  void applyToMetric(String name, Receiver<GenericMetric<Object, ?>> callback) {
    delegate.applyToMetric(name, callback);
  }

  @Override
  void applyToMetricsBeneath(String directory,
      Receiver<GenericMetric<Object, ?>> callback) {
    delegate.applyToMetricsBeneath(directory, callback);
  }

  @Override
  void applyToMetrics(Iterable<String> names,
      Receiver<GenericMetric<Object, ?>> callback) {
    delegate.applyToMetrics(names, callback);
  }

  @Override
  void applyToMetricMetadata(Receiver<GenericMetric<Object, ?>> callback) {
    delegate.applyToMetricMetadata(callback);
  }

  /**
   * @deprecated Use {@code new TestMetricFactory()} instead.
   */
  // Do not remove this method. This static method 'override' hides MetricFactory.getDefault().
  @Deprecated
  public static MetricFactory getDefault() {
    throw new UnsupportedOperationException("Use new TestMetricFactory() instead.");
  }
}