package io.grpc.zpages;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;

public class ZpageBindableService implements BindableService {
    @Override
    public ServerServiceDefinition bindService() {
        MethodDescriptor<String, String> methodDescriptor = MethodDescriptor.create(
                MethodDescriptor.MethodType.UNARY,
                "/ZpageService/zpage",
                new ZpageRequestMarshaller(),
                new ZpageResponseMarshaller());
        ServiceDescriptor serviceDescriptor = new ServiceDescriptor("/ZpageService", methodDescriptor);
        ServerServiceDefinition.Builder serverServiceDefinition = ServerServiceDefinition.builder(serviceDescriptor);
        ServerMethodDefinition<String, String> serverMethodDefinition = ServerMethodDefinition.create(methodDescriptor,
                new ZpageServerCallHandler());
        serverServiceDefinition.addMethod(serverMethodDefinition);
        return serverServiceDefinition.build();
    }
}