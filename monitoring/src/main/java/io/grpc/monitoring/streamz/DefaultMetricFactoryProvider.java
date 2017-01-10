package io.grpc.monitoring.streamz;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * A provider for MetricFactory (used as a just-in-time Guice binding)  that always returns
 * {@link MetricFactory#getDefault()}.
 *
 * <p>Note: This class implements com.google.inject.Provider and not javax.inject.Provider because
 * the {@literal @}ProvidedBy annotation requires a reference to a subclass of the former.
 */
final class DefaultMetricFactoryProvider implements Provider<MetricFactory> {
    @Inject
    DefaultMetricFactoryProvider() {}

    @Override
    public MetricFactory get() {
        return MetricFactory.getDefault();
    }
}