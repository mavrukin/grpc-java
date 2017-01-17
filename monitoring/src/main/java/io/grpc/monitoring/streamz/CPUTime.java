package io.grpc.monitoring.streamz;

public class CPUTime {
    private final double userTime;
    private final double systemTime;

    public CPUTime(double userTime, double systemTime) {
        this.userTime = userTime;
        this.systemTime = systemTime;
    }

    public double getUserTime() {
        return userTime;
    }

    public double getSystemTime() {
        return systemTime;
    }
}