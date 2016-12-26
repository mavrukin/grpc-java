/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.zpages;

import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.Expvar.EV_KeyValueList;
import io.grpc.status.proto.Expvar.EV_KeyValuePair;
import io.grpc.status.proto.ServerStatusGrpc.ServerStatusImplBase;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;

public class ServerStatusImpl extends ServerStatusImplBase {
  private static final Logger logger = Logger.getLogger(ServerStatusImpl.class.getName());
  private static final StatsCollector statsCollector = new StatsCollector();

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