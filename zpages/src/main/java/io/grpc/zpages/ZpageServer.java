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

import io.grpc.Server;
import io.grpc.ServerBuilder;
// import io.grpc.status.proto.GrpcStatus;

import java.io.IOException;
import java.util.logging.Logger;

public class ZpageServer {
  private static final Logger logger = Logger.getLogger(ZpageServer.class.getName());
  private final int port;
  private final Server server;

  private ZpageServer(int port) {
    this.port = port;
    ServerBuilder serverBuilder = ServerBuilder.forPort(this.port);
    serverBuilder.addService(new ZpageBindableService());
    this.server = serverBuilder.build();
  }

  private void start() throws IOException {
    server.start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            ZpageServer.this.stop();
            System.err.println("*** server shut down");
        }
    });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Run the mini zpage serve experiment.
   * @param args No arguments need to be passed.
   * @throws IOException Can throw this exception due to an IO Error.
   * @throws InterruptedException Can throw this exception when it is pre-empted.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    ZpageServer zpageServer = new ZpageServer(8980);
    zpageServer.start();
    zpageServer.blockUntilShutdown();
  }
}