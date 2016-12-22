package io.grpc.zpages;

import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.Expvar.EV_KeyValueList;
import io.grpc.status.proto.Expvar.EV_KeyValuePair;
import io.grpc.status.proto.ServerStatusGrpc.ServerStatusImplBase;
import io.grpc.stub.StreamObserver;
import java.util.logging.Logger;

public class ServerStatusImpl extends ServerStatusImplBase {
  private static final Logger logger = Logger.getLogger(ServerStatusImpl.class.getName());

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
}