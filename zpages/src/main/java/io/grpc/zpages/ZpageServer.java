package io.grpc.zpages;

import io.grpc.Server;
import io.grpc.ServerBuilder;

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
        if(server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if(server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ZpageServer zpageServer = new ZpageServer(8980);
        zpageServer.start();
        zpageServer.blockUntilShutdown();
    }
}