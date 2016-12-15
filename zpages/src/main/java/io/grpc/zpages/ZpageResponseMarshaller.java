package io.grpc.zpages;

import io.grpc.MethodDescriptor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ZpageResponseMarshaller implements MethodDescriptor.Marshaller<String> {

    @Override
    public InputStream stream(String value) {
        String helloWorld = "Hello World\n";
        return new ByteArrayInputStream((helloWorld + " [" + value + "]").getBytes());
    }

    @Override
    public String parse(InputStream stream) {
        return new BufferedReader(
                new InputStreamReader(stream))
            .lines()
            .collect(Collectors.joining("\n"));
    }
}