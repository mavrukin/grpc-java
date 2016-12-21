package io.grpc.zpages;

import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusService {
  private static final Logger logger = Logger.getLogger(ServerStatusService.class.getName());
  private Server server;
  private ScheduledExecutorService executorService;
  private static final int port = 8123;

  private void start() throws IOException {
    executorService = Executors.newSingleThreadScheduledExecutor();
    server = NettyServerBuilder.forPort(port)
        .addService(ServerInterceptors.intercept(
            new ServerStatusImpl(executorService))).build().start();
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdownNow();
      if(!server.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.log(Level.SEVERE, "Timedout waiting for server shutdown");
      }
      MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.SECONDS);
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