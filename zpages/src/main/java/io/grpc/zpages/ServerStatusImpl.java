package io.grpc.zpages;

import static io.grpc.stub.ServerCalls.asyncUnaryCall;

import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.Expvar.EV_KeyValueList;
import io.grpc.status.proto.Expvar.EV_KeyValuePair;
import io.grpc.status.proto.ServerStatusGrpc;
import io.grpc.status.proto.ServerStatusGrpc.ServerStatusImplBase;
import io.grpc.stub.ServerCalls.UnaryMethod;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

public class ServerStatusImpl extends ServerStatusImplBase {
  private static final Logger logger = Logger.getLogger(ServerStatusImpl.class.getName());
  private final ScheduledExecutorService executorService;

  public ServerStatusImpl(ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  public void getExportedVariables(EmptyMessage request,
      StreamObserver<EV_KeyValueList> responseObserver) {
    logger.info("Request received");
    EV_KeyValueList.Builder ev_keyValueListBuilder = EV_KeyValueList.newBuilder();
    EV_KeyValuePair.Builder ev_keyValuePairBuilder = EV_KeyValuePair.newBuilder();

    ev_keyValuePairBuilder.setName("TEST");
    ev_keyValuePairBuilder.setValue("*** TEST ***");
    ev_keyValuePairBuilder.setDocstring("DOC TEST DOC");
    ev_keyValueListBuilder.addPair(ev_keyValuePairBuilder.build());

    responseObserver.onNext(ev_keyValueListBuilder.build());
    responseObserver.onCompleted();
  }

  /*
  @Override
  public ServerServiceDefinition bindService() {
    logger.info("Binding Service...");
    ServerServiceDefinition.Builder serverServiceDefinitionBuilder =
        ServerServiceDefinition.builder(ServerStatusGrpc.getServiceDescriptor().getName());
    // MethodDescriptor methodDescriptor = ServerStatusGrpc.METHOD_GET_EXPORTED_VARIABLES;

    MethodDescriptor<EmptyMessage, EV_KeyValueList> GET_EXPORTED_VARIABLES =
        MethodDescriptor.create(
            ServerStatusGrpc.METHOD_GET_EXPORTED_VARIABLES.getType(),
            ServerStatusGrpc.METHOD_GET_EXPORTED_VARIABLES.getFullMethodName(),
            ProtoUtils.jsonMarshaller(EmptyMessage.getDefaultInstance()),
            ProtoUtils.jsonMarshaller(EV_KeyValueList.getDefaultInstance()));

    ServerCallHandler<EmptyMessage, EV_KeyValueList> serverCallHandler = asyncUnaryCall(
        new UnaryMethod<EmptyMessage, EV_KeyValueList>() {
          @Override
          public void invoke(EmptyMessage request, StreamObserver<EV_KeyValueList> streamObserver) {
            getExportedVariables(request, streamObserver);
          }
        });
    serverServiceDefinitionBuilder.addMethod(GET_EXPORTED_VARIABLES, serverCallHandler);
    return serverServiceDefinitionBuilder.build();
  } */
}