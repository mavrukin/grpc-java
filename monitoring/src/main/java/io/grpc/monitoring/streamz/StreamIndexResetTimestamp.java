package io.grpc.monitoring.streamz;

public class StreamIndexResetTimestamp {
    private final int streamIndex;
    private final long resetTimestamp;

    public StreamIndexResetTimestamp(int streamIndex, long timestamp) {
        this.streamIndex = streamIndex;
        this.resetTimestamp = timestamp;
    }

    public int getStreamIndex() {
        return streamIndex;
    }

    public long getResetTimestamp() {
        return resetTimestamp;
    }
}