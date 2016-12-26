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

import io.grpc.ManagedChannel;
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

  /**
   * Constructor for the client.
   * @param host The host to connect to for the server
   * @param port The port to connect to on the host server
   */
  public ServerStatusClient(String host, int port) {
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    managedChannel = NettyChannelBuilder.forAddress(new InetSocketAddress(address,port))
        .flowControlWindow(65 * 1024)
        .negotiationType(NegotiationType.PLAINTEXT).build();
    serverStatusStub = ServerStatusGrpc.newBlockingStub(managedChannel);
  }

  /**
   * Shutds down the client.
   * @throws InterruptedException thrown if client fails to shutdown in 5 seconds
   */
  public void shutdown() throws InterruptedException {
    managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * Executes a request against the server.
   */
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
    if (ev_keyValueList != null) {
      for (EV_KeyValuePair ev_keyValuePair : ev_keyValueList.getPairList()) {
        System.out.println(ev_keyValuePair.getName() + ": " + ev_keyValuePair.getValue() + " ["
            + ev_keyValuePair.getDocstring() + "]");
      }
    }
  }

  /**
   * The client runner for the corresponding server (Java Version).
   * @param args Ignores command line arguments
   * @throws InterruptedException thrown during runtime
   */
  public static void main(String[] args) throws InterruptedException {
    final ServerStatusClient serverStatusClient = new ServerStatusClient("localhost", 8123);
    try {
      for (int i = 0; i < 10; ++i) {
        System.out.println("Doing request: " + i);
        serverStatusClient.doRequest();
        Thread.sleep(1000);
      }
    } finally {
      serverStatusClient.shutdown();
    }
  }
}