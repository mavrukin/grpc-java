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

import com.google.common.base.Preconditions;

import io.grpc.Status;
import io.grpc.status.proto.EmptyMessage;
import io.grpc.status.proto.SimpleGrpc.SimpleImplBase;
import io.grpc.status.proto.SimpleService.EchoRequest;
import io.grpc.status.proto.SimpleService.EchoResponse;
import io.grpc.status.proto.SimpleService.FailWithProbabilityOrSucceedEchoRequest;
import io.grpc.stub.StreamObserver;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.Logger;

public class SimpleServiceImpl extends SimpleImplBase {
  private static final Logger logger = Logger.getLogger(SimpleServiceImpl.class.getName());
  protected static final DateFormat DATE_FORMAT = new SimpleDateFormat("[HH:mm:ss.SSS dd/MM/yyyy]");
  private static final Random RANDOM = new Random(System.currentTimeMillis());

  @Override
  public void noop(EmptyMessage request, StreamObserver<EmptyMessage> responseObserver) {
    logger.info("no-op request received at " + DATE_FORMAT.format(
        new Date(System.currentTimeMillis())));
    responseObserver.onNext(EmptyMessage.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void echo(EchoRequest echoRequest, StreamObserver<EchoResponse> responseObserver) {
    logger.info("echo request for string: " + echoRequest.getEcho() + " with repetitions: "
        + echoRequest.getRepeatEcho() + " received at " + DATE_FORMAT.format(
          new Date(System.currentTimeMillis())));

    responseObserver.onNext(buildEchoResponseFromEchoRequest(echoRequest));
    responseObserver.onCompleted();
  }

  @Override
  public void failPlease(FailWithProbabilityOrSucceedEchoRequest request,
      StreamObserver<EchoResponse> responseObserver) {
    EchoRequest echoRequest = request.getEchoRequest();
    int failProbability = request.getFailProbability();
    Preconditions.checkArgument(failProbability >= 0 && failProbability <= 100,
        "fail probability not [" + failProbability + "] not in range [0, 100] inclusive");
    logger.info("fail please - p(" + failProbability + " / 100)" + " echo: " + echoRequest.getEcho()
        + " with " + "repetitions: " + echoRequest.getRepeatEcho() + " received at "
        + DATE_FORMAT.format(new Date(System.currentTimeMillis())));

    int randomFail = RANDOM.nextInt(100);
    if (randomFail < failProbability) {
      Status status = Status.INTERNAL;
      status = status.withCause(new FailPleaseException("Looks like you hit jackpot - we failed!"));
      responseObserver.onError(status.asRuntimeException());
    } else {
      responseObserver.onNext(buildEchoResponseFromEchoRequest(echoRequest));
      responseObserver.onCompleted();
    }
  }

  private static class FailPleaseException extends Exception {
    private String message;

    FailPleaseException(String message) {
      this.message = message;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  private static EchoResponse buildEchoResponseFromEchoRequest(EchoRequest echoRequest) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < echoRequest.getRepeatEcho(); ++i) {
      stringBuilder.append(echoRequest.getEcho());
    }
    EchoResponse.Builder echoResponseBuilder = EchoResponse.newBuilder();
    echoResponseBuilder.setEchoResponse(stringBuilder.toString());
    return echoResponseBuilder.build();
  }
}