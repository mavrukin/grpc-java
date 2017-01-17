package io.grpc.monitoring.streamz;

public class ValueAtTimestamp<V> {
    private final V value;
    private final Long timestamp;

    protected ValueAtTimestamp(Long timestamp, V value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}