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
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusService {
  private static final Logger logger = Logger.getLogger(ServerStatusService.class.getName());
  private Server server;
  private static final int port = 8123;

  private void start() throws IOException {
    server = NettyServerBuilder.forPort(port)
        .addService(ServerInterceptors.intercept(
            new ServerStatusImpl()))
        .addService(ServerInterceptors.intercept(
            new SimpleServiceImpl()))
        .addService(ServerInterceptors.intercept(
            new LessSimpleServiceImpl("localhost", port))).build().start();
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdownNow();
      if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.log(Level.SEVERE, "Timedout waiting for server shutdown");
      }
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Basic runner for the service.
   * @param args Ignore for all intents and purposes command line arguments
   * @throws IOException thrown during server startup time
   * @throws InterruptedException thrown during server shutdown
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final ServerStatusService serverStatusService = new ServerStatusService();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          System.err.println("Shutting down Server Status... server shutting down");
          serverStatusService.stop();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });

    serverStatusService.start();
    logger.info("Server started, listening on port: " + port);
    serverStatusService.blockUntilShutdown();
  }
}