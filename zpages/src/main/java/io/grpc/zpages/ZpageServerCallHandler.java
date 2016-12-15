package io.grpc.zpages;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

public class ZpageServerCallHandler implements ServerCallHandler<String, String> {

    @Override
    public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
        return null;
    }
}