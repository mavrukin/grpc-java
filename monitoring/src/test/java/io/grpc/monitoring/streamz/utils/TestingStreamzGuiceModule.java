package io.grpc.monitoring.streamz.utils;

import com.google.inject.AbstractModule;
import io.grpc.monitoring.streamz.MetricFactory;
import io.grpc.monitoring.streamz.TestMetricFactory;

/**
 * Module for binding Streamz abstractions in tests.
 *
 * <p> See {@link io.grpc.monitoring.streamz.StreamzGuiceModule} for
 * binding production Streamz abstractions.
 *
 * @author treitel@google.com (Richard Treitel)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class TestingStreamzGuiceModule extends AbstractModule {
  protected void configure() {
    bind(MetricFactory.class).to(TestMetricFactory.class);
  }
}