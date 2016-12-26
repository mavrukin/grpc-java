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
import io.grpc.status.proto.SimpleService.DoNEchoRequestsAndFailSomeRequest;
import io.grpc.status.proto.SimpleService.FailWithProbabilityOrSucceedEchoRequest;
import io.grpc.stub.StreamObserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

  @Override
  public void doNEmptyRequests(DoNEmptyRequestsRequest request,
      StreamObserver<DoNEmptyRequestsResponse> responseObserver) {
    logger.info("do n empty requests - " + request.getNumEmptyRequest() + " at parallel level: "
        + request.getPLevel() + " request received at "
        + SimpleServiceImpl.DATE_FORMAT.format(new Date(System.currentTimeMillis())));
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
      final ManagedChannel managedChannel = NettyChannelBuilder.forAddress(
          new InetSocketAddress(address, port)).flowControlWindow(65 * 1024)
          .negotiationType(NegotiationType.PLAINTEXT).build();

      int threadPoolSize = request.getPLevel() != 0 ? request.getPLevel() : 1;
      ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

      long startRequestsTime = System.nanoTime();
      final AtomicLong maxRequestTime = new AtomicLong(0);
      final AtomicInteger successCounter = new AtomicInteger(0);
      final ArrayList<Future> submittedTasks = new ArrayList<Future>();
      for(int i = 0; i < request.getNumEmptyRequest(); ++i) {
        submittedTasks.add(executorService.submit(new Runnable() {
          @Override
          public void run() {
            SimpleGrpc.SimpleBlockingStub stub = SimpleGrpc.newBlockingStub(managedChannel);
            long startRequestTime = System.nanoTime();
            EmptyMessage response = stub.noop(EmptyMessage.getDefaultInstance());
            if(response != null) {
              successCounter.incrementAndGet();
            }
            long endRequestTime = System.nanoTime();
            synchronized (maxRequestTime) {
              if (endRequestTime - startRequestTime > maxRequestTime.get()) {
                maxRequestTime.set(endRequestTime - startRequestTime);
              }
            }
          }
        }));
      }
      for(Future task : submittedTasks) {
        task.get();
      }
      long endRequestsTime = System.nanoTime();

      long totalTime = endRequestsTime - startRequestsTime;
      DoNEmptyRequestsResponse.Builder doNEmptyRequestsResponseBuilder =
          DoNEmptyRequestsResponse.newBuilder();
      doNEmptyRequestsResponseBuilder.setTotalProcessTime(totalTime);
      doNEmptyRequestsResponseBuilder.setLongestRequest(maxRequestTime.get());
      doNEmptyRequestsResponseBuilder.setSuccessfulRequests(successCounter.get());

      responseObserver.onNext(doNEmptyRequestsResponseBuilder.build());
    } catch (UnknownHostException e) {
      responseObserver.onError(e);
    } catch (InterruptedException e) {
      responseObserver.onError(e);
    } catch (ExecutionException e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void doNRequestsAndFailSome(DoNEchoRequestsAndFailSomeRequest request,
                                     StreamObserver<DoNEmptyRequestsResponse> responseObserver) {
    BlockForMillisRequest blockForMillisRequest = null;
    if (request.hasBlockForMillis()) {
      blockForMillisRequest = request.getBlockForMillis();
    }
    DoNEmptyRequestsRequest doNEmptyRequestsRequest = null;
    if (request.hasEmptyRequests()) {
      doNEmptyRequestsRequest = request.getEmptyRequests();
    }
    FailWithProbabilityOrSucceedEchoRequest failWithProbabilityOrSucceedEchoRequest = null;
    if (request.hasEchoWithPFailure()) {
      failWithProbabilityOrSucceedEchoRequest = request.getEchoWithPFailure();
    }

  }
}