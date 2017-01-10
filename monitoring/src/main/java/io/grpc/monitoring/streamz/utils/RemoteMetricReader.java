package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.MonitorRequest;
import io.grpc.monitoring.streamz.proto.MonitorResponse;
import io.grpc.monitoring.streamz.proto.StreamzGrpc;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Reader for metrics exported in another process, via the Streamz stubby
 * service.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class RemoteMetricReader extends MetricReader {
  private final ManagedChannel managedChannel;
  private final StreamzGrpc.StreamzBlockingStub streamzStub;

  RemoteMetricReader(ManagedChannel managedChannel) {
    this(managedChannel.authority());
  }

  RemoteMetricReader(String authority) {
    String[] splits = authority.split(":");
    Preconditions.checkArgument(splits.length == 2, "authority needs to be in format of host:port");
    String host = splits[0];
    int port = Integer.parseInt(splits[1]);

    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    managedChannel = NettyChannelBuilder.forAddress(new InetSocketAddress(address, port))
        .flowControlWindow(65 * 1024)
        .negotiationType(NegotiationType.PLAINTEXT).build();
    streamzStub = StreamzGrpc.newBlockingStub(managedChannel);
  }

  RemoteMetricReader(String host, int port) {
    this(String.format("%s:%d", host, port));
  }

  protected MonitorEvent getData(MonitorRequest request) {
    Preconditions.checkArgument(request.getNumEvents() == 1);
    final List<MonitorEvent> events = Lists.newArrayListWithExpectedSize(1);

    MonitorResponse monitorResponse = streamzStub.getStream(request);
    events.add(monitorResponse.getMonitorEvent());

    Preconditions.checkState(events.size() == 1, "Got more than one event from streamz.");
    return events.get(0);
  }
}