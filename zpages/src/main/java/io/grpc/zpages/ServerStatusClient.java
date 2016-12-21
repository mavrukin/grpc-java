package io.grpc.zpages;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.Expvar.EV_KeyValueList;
import io.grpc.status.proto.Expvar.EV_KeyValuePair;
import io.grpc.status.proto.ServerStatusGrpc;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusClient {
  private static final Logger logger = Logger.getLogger(ServerStatusClient.class.getName());
  private final ManagedChannel managedChannel;
  private final ServerStatusGrpc.ServerStatusBlockingStub serverStatusStub;

  public ServerStatusClient(String host, int port) {
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    }
    catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    managedChannel = NettyChannelBuilder.forAddress(new InetSocketAddress(address,port))
        .flowControlWindow(65 * 1024)
        .negotiationType(NegotiationType.PLAINTEXT).build();
    serverStatusStub = ServerStatusGrpc.newBlockingStub(managedChannel);
  }

  public void shutdown() throws InterruptedException {
    managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public void doRequest() {
    logger.info("Running request...");
    EmptyMessage emptyMessage = EmptyMessage.getDefaultInstance();
    EV_KeyValueList ev_keyValueList;
    try {
      ev_keyValueList = serverStatusStub.getExportedVariables(emptyMessage);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC Failed {0}", e.getStatus());
      return;
    }
    if(ev_keyValueList != null) {
      for (EV_KeyValuePair ev_keyValuePair : ev_keyValueList.getPairList()) {
        System.out.println(ev_keyValuePair.getName() + ": " + ev_keyValuePair.getValue() + " [" +
          ev_keyValuePair.getDocstring() + "]");
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {
    final ServerStatusClient serverStatusClient = new ServerStatusClient("localhost", 8123);
    try {
      for(int i = 0; i < 10; ++i) {
        System.out.println("Doing request: " + i);
        serverStatusClient.doRequest();
        Thread.sleep(1000);
      }
    } finally {
      serverStatusClient.shutdown();
    }
  }
}