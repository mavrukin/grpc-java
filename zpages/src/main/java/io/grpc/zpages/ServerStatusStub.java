package io.grpc.zpages;

import static io.grpc.stub.ClientCalls.blockingUnaryCall;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.Expvar.EV_KeyValueList;
import io.grpc.status.proto.ServerStatusGrpc;
import io.grpc.stub.AbstractStub;

public class ServerStatusStub extends AbstractStub<ServerStatusStub> {
  private static final MethodDescriptor<EmptyMessage, EV_KeyValueList> GET_EXPORTED_VARIABLES =
      MethodDescriptor.create(
          ServerStatusGrpc.METHOD_GET_EXPORTED_VARIABLES.getType(),
          ServerStatusGrpc.METHOD_GET_EXPORTED_VARIABLES.getFullMethodName(),
          ProtoUtils.jsonMarshaller(EmptyMessage.getDefaultInstance()),
          ProtoUtils.jsonMarshaller(EV_KeyValueList.getDefaultInstance()));

  protected ServerStatusStub(Channel channel) {
    super(channel);
  }

  protected ServerStatusStub(Channel channel, CallOptions callOptions) {
    super(channel, callOptions);
  }

  @Override
  protected ServerStatusStub build(Channel channel, CallOptions callOptions) {
    return new ServerStatusStub(channel, callOptions);
  }

  public EV_KeyValueList doRequest(EmptyMessage request) {
    return blockingUnaryCall(getChannel(), GET_EXPORTED_VARIABLES, getCallOptions(), request);
  }
}