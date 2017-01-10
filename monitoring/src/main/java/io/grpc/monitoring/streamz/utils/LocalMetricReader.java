package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.MetricFactory;
import io.grpc.monitoring.streamz.MonitorStreamsContext;
import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.MonitorRequest;

/**
 * Reader for metrics exported in the local runtime.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class LocalMetricReader extends MetricReader {
  private final MetricFactory metricFactory;

  public LocalMetricReader() {
    this(MetricFactory.getDefault());
  }

  public LocalMetricReader(MetricFactory metricFactory) {
    this.metricFactory = Preconditions.checkNotNull(metricFactory);
  }

  @Override
  protected MonitorEvent getData(MonitorRequest request) {
    return MonitorStreamsContext.collectSingleRequest(metricFactory, request);
  }
}