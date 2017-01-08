package io.grpc.monitoring.streamz;

import javax.annotation.Nullable;

/**
 * This is an externalization of a Google internal helper interface.  The reason it is
 * being added here is to maintain Java 6 compatability.  Beginning with Java 8 the Consumer
 * interface in the function package can be used in its place.
 */
public interface Receiver<T> {
    void accept(@Nullable T object);
}