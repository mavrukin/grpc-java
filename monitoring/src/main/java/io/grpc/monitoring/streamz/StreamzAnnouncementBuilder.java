package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.proto.RootDescriptor;
import io.grpc.monitoring.streamz.proto.StreamzAnnouncement;

/**
 * Builds {@link StreamzAnnouncement} defaulted to the host of the current process.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author yjbanov@google.com (Yegor Jbanov)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class StreamzAnnouncementBuilder {
  private final StreamzAnnouncement.Builder delegate;
  private RootDescriptor rootDescriptor;
  private Long processStartTime;
  private Long currentTime;

  /**
   * Private constructor to prevent instantiation outside this class.
   */
  private StreamzAnnouncementBuilder() {
    delegate = StreamzAnnouncement.newBuilder();
  }

  /**
   * Creates a {@link StreamzAnnouncementBuilder} specific to the host on which this process
   * runs.
   */
  static StreamzAnnouncementBuilder newBuilder() {
    return new StreamzAnnouncementBuilder();
  }

  StreamzAnnouncementBuilder setRootDescriptor(RootDescriptor rootDescriptor) {
    this.rootDescriptor = rootDescriptor;
    return this;
  }

  StreamzAnnouncementBuilder setProcessStartTime(Long processStartTime) {
    this.processStartTime = processStartTime;
    return this;
  }

  StreamzAnnouncementBuilder setCurrentTime(Long currentTime) {
    this.currentTime = currentTime;
    return this;
  }

  StreamzAnnouncementBuilder addRpcServerPort(Integer rpcServerPort) {
    delegate.addRpcserverPort(rpcServerPort);
    return this;
  }

  StreamzAnnouncement build() {
    Preconditions.checkNotNull(rootDescriptor, "A root descriptor cannot be null");
    Preconditions.checkNotNull(rootDescriptor.getName(), "A root name cannot be null");
    return delegate
        .setRoot(rootDescriptor.getName())
        .addAllLabel(rootDescriptor.getLabelsList())
        .setHostname(Utils.getHostname())
        .setBirthTimestamp(
            processStartTime == null ? Utils.getProcessStartTimeMicros() : processStartTime)
        .setAnnouncementTimestamp(
            currentTime == null ? Utils.getCurrentTimeMicros() : currentTime)
        .build();
  }
}