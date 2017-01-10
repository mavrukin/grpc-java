package io.grpc.monitoring.streamz;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import org.joda.time.Instant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Data class that contains information about root collection process.
 */
final class RootCollectionInfo {
    /** Name of a server that the collected data is sent to. */
    final String serverName;

    /** Map from target keys to their corresponding target collection information. */
    final Map<ByteString, TargetCollectionInfo> targetCollectionInfoMap;

    /** Most recent status from a ReadSchedules RPC. */
    final Status readSchedulesStatus;

    /** Time of the most recent ReadSchedules success. */
    final Instant lastReadSchedulesSuccess;

    RootCollectionInfo(String serverName,
                       Map<ByteString, TargetCollectionInfo> targetCollectionInfoMap, Status readSchedulesStatus,
                       Instant lastReadSchedulesSuccess) {
        this.serverName = serverName;
        this.targetCollectionInfoMap = targetCollectionInfoMap;
        this.readSchedulesStatus = readSchedulesStatus;
        this.lastReadSchedulesSuccess = lastReadSchedulesSuccess;
    }

    static final class WriteRpcInfo {
        /** A recent Write RPC failure, or Status.OK. */
        final Status writeStatus;

        /** Time when writeStatus was generated. Valid when writeStatus is not OK. */
        final Instant writeStatusTime;

        // TODO(avrukin) do we want to support TrafficClass?
        /** Traffic class attempted for use by writeStatus. Valid when writeStatus is not OK. */
        // final TrafficClass writeStatusClass;

        /** Time of the most recent successful Write. */
        final Instant lastSuccessfulWriteTime;

        WriteRpcInfo(Status writeStatus, Instant writeStatusTime, /* TrafficClass writeStatusClass, */
                     Instant lastSuccessfulWriteTime) {
            this.writeStatus = writeStatus;
            this.writeStatusTime = writeStatusTime;
            // TODO(avrukin) TrafficClass
            // this.writeStatusClass = writeStatusClass;
            this.lastSuccessfulWriteTime = lastSuccessfulWriteTime;
        }
    }

    /**
     * Data class that contains information about target collection process.
     */
    static final class TargetCollectionInfo {
        /** Timestamp of the last update of collection schedules. */
        final Instant lastSchedulesUpdate;

        /** Timestamp of the last update of collection schedules which got processed and applied. */
        final Instant lastProcessedSchedulesUpdate;

        /** List of all *effective* collection schedules for a target. */
        final List<TargetSchedule> targetSchedules;

        // TODO(avrukin) TrafficClass?
        /** Information about recent Write RPC results, by traffic class. */
        // final Map<TrafficClass, WriteRpcInfo> writeRpcInfoMap;

        TargetCollectionInfo(Instant lastSchedulesUpdate, Instant lastProcessedSchedulesUpdate) {
            this.lastSchedulesUpdate = lastSchedulesUpdate;
            this.lastProcessedSchedulesUpdate = lastProcessedSchedulesUpdate;
            this.targetSchedules = ImmutableList.of();
            // this.writeRpcInfoMap = ImmutableMap.of();
        }

        TargetCollectionInfo(Instant lastSchedulesUpdate, Instant lastProcessedSchedulesUpdate,
                             List<TargetSchedule> targetSchedules /*, Map<TrafficClass, WriteRpcInfo> writeRpcInfoMap*/) {
            this.lastSchedulesUpdate = lastSchedulesUpdate;
            this.lastProcessedSchedulesUpdate = lastProcessedSchedulesUpdate;
            this.targetSchedules = targetSchedules;
            // TODO(avrukin) TrafficClass?
            // this.writeRpcInfoMap = writeRpcInfoMap;
        }
    }

    /**
     * Data class that contains a single collection schedule for a target.
     *
     * @see com.google.protos.monitoring.streamz.Monarch.ReadSchedulesResponse.TargetInfo.Schedule
     */
    static final class TargetSchedule {
        final long samplingPeriodNanos;
        final List<String> inclusionPatterns;
        final List<String> exclusionPatterns;
        // TODO(avrukin) TrafficClass?
        // final TrafficClass trafficClass;
        final int replicationLevel;

        TargetSchedule(long samplingPeriodNanos, List<String> inclusionPatterns,
                       List<String> exclusionPatterns, /* TrafficClass trafficClass,*/ int replicationLevel) {
            this.samplingPeriodNanos = samplingPeriodNanos;
            this.inclusionPatterns = inclusionPatterns;
            this.exclusionPatterns = exclusionPatterns;
            // TODO(avrukin) TrafficClass?
            // this.trafficClass = trafficClass;
            this.replicationLevel = replicationLevel;
        }

        static final Ordering<TargetSchedule> BY_SAMPLING_PERIOD_ASC =
                Ordering.natural().onResultOf(new Function<TargetSchedule, Long>() {
                    @Override
                    public Long apply(TargetSchedule schedule) {
                        return schedule.samplingPeriodNanos;
                    }
                });

        /*
            TODO(avrukin) TraffiClass?
        static final Ordering<TargetSchedule> BY_TRAFFIC_CLASS_DESC =
                Ordering.from(new TrafficClassComparator()).reverse().onResultOf(
                        new Function<TargetSchedule, TrafficClass>() {
                            @Override
                            public TrafficClass apply(TargetSchedule schedule) {
                                return schedule.trafficClass;
                            }
                        });
        */

        static final Ordering<TargetSchedule> BY_REPLICATION_LEVEL_DESC =
                Ordering.natural().reverse().onResultOf(new Function<TargetSchedule, Integer>() {
                    @Override
                    public Integer apply(TargetSchedule schedule) {
                        return schedule.replicationLevel;
                    }
                });

        static final Ordering<TargetSchedule> ORDERING = Ordering.compound(
                ImmutableList.of(BY_SAMPLING_PERIOD_ASC, /*BY_TRAFFIC_CLASS_DESC,*/ BY_REPLICATION_LEVEL_DESC));

        @Override
        public int hashCode() {
            return Arrays.hashCode(new Object[] {samplingPeriodNanos, inclusionPatterns, exclusionPatterns,
                    /* trafficClass,*/ replicationLevel});
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TargetSchedule that = (TargetSchedule) obj;
            return equals(this.samplingPeriodNanos, that.samplingPeriodNanos)
                    && equals(this.inclusionPatterns, that.inclusionPatterns)
                    && equals(this.exclusionPatterns, that.exclusionPatterns)
                    // TODO(avrukin) TrafficClass?
                    // && this.trafficClass == that.trafficClass
                    && this.replicationLevel == that.replicationLevel;
        }

        private static boolean equals(Object a, Object b) {
            return (a == b) || (a != null && a.equals(b));
        }
    }
}