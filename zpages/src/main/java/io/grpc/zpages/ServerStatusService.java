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
            new ServerStatusImpl())).build().start();
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdownNow();
      if(!server.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.log(Level.SEVERE, "Timedout waiting for server shutdown");
      }
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

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