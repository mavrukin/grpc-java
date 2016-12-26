package io.grpc.zpages;

import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.LessSimpleGrpc.LessSimpleImplBase;
import io.grpc.status.proto.SimpleGrpc;
import io.grpc.status.proto.SimpleService.BlockForMillisRequest;
import io.grpc.status.proto.SimpleService.DoNEmptyRequestsRequest;
import io.grpc.status.proto.SimpleService.DoNEmptyRequestsResponse;
import io.grpc.stub.StreamObserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.Logger;

public class LessSimpleServiceImpl extends LessSimpleImplBase {
  private static final Logger logger = Logger.getLogger(LessSimpleServiceImpl.class.getName());
  private String host;
  private int port;

  public LessSimpleServiceImpl(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public void blockForMillis(BlockForMillisRequest request,
      StreamObserver<EmptyMessage> responseObserver) {
    logger.info("blocking for millis [" + request.getMillis() + "] request received at " +
      SimpleServiceImpl.DATE_FORMAT.format(new Date(System.currentTimeMillis())));
    try {
      Thread.sleep(request.getMillis());
      responseObserver.onNext(EmptyMessage.getDefaultInstance());
    } catch (InterruptedException e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void doNEmptyRequests(DoNEmptyRequestsRequest request,
      StreamObserver<DoNEmptyRequestsResponse> responseObserver) {
    logger.info("do n empty requests - " + request.getNumEmptyRequest() + " at parallel level: "
        + request.getPLevel() + " request received at "
        + SimpleServiceImpl.DATE_FORMAT.format(new Date(System.currentTimeMillis())));
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
      ManagedChannel managedChannel = NettyChannelBuilder.forAddress(
          new InetSocketAddress(address, port)).flowControlWindow(65 * 1024)
          .negotiationType(NegotiationType.PLAINTEXT).build();
      SimpleGrpc.SimpleBlockingStub stub = SimpleGrpc.newBlockingStub(managedChannel);
      long startRequestsTime = System.nanoTime();
      for(int i = 0; i < request.getNumEmptyRequest(); ++i) {
        stub.noop(EmptyMessage.getDefaultInstance());
      }
      long endRequestsTime = System.nanoTime();
      long totalTime = endRequestsTime - startRequestsTime;
      DoNEmptyRequestsResponse.Builder doNEmptyRequestsResponseBuilder =
          DoNEmptyRequestsResponse.newBuilder();
      doNEmptyRequestsResponseBuilder.setTotalProcessTime(totalTime);
      responseObserver.onNext(doNEmptyRequestsResponseBuilder.build());
    } catch (UnknownHostException e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }
}