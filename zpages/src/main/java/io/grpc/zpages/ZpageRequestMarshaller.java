package io.grpc.zpages;

import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;

public class ZpageRequestMarshaller implements MethodDescriptor.Marshaller<String> {

    @Override
    public InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes());
    }

    @Override
    public String parse(InputStream stream) {
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}