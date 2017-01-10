package io.grpc.monitoring.streamz.utils;

import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.MonitorRequest;
import io.grpc.monitoring.streamz.proto.StreamQuery;

/**
 * Reader of Streamz metrics, either in-process or remotely.
 *
 * @see LocalMetricReader
 * @see RemoteMetricReader
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
abstract class MetricReader {
  abstract MonitorEvent getData(MonitorRequest request);

  /**
   * Reads a snapshot of Streamz metrics matching the given pattern.
   *
   * @param pattern If pattern ends in a '/', then it will match all metrics
   *     with that prefix. Otherwise it matches metrics with exactly that name.
   */
  public StreamzSnapshot snapshot(String pattern) {
    MonitorRequest request = MonitorRequest.newBuilder()
        .setNumEvents(1)
        .setTimestamps(true)
        .setIncludeDeclarationMetadata(true)
        .addQuery(StreamQuery.newBuilder().setPattern(pattern))
        .build();
    return new StreamzSnapshot(getData(request));
  }

  /**
   * Reads a snapshot of all Streamz metrics.
   */
  public final StreamzSnapshot snapshotAll() {
    return snapshot("/");
  }
}