package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.monitoring.streamz.proto.MonitorEvent;
import io.grpc.monitoring.streamz.proto.MonitorRequest;
import io.grpc.monitoring.streamz.GenericCell.CellKey;
import io.grpc.monitoring.streamz.proto.StreamDefinition;
import io.grpc.monitoring.streamz.proto.StreamQuery;
import io.grpc.monitoring.streamz.proto.StreamValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects a sample of metrics into a {@link MonitorEvent} given a
 * {@link MonitorRequest} and a {@link MetricFactory}.
 *
 * <p>Access to the stream/metric index map is not synchronized.
 * This must only be used by one thread at a time.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@VisibleForTesting
public class MonitorStreamsContext {
    private final MonitorRequest request;
    private final boolean emptyStringMatchesAllMetrics;
    // The next stream index that will be assigned
    private int nextStreamIndex = 0;
    private final Map<CellKey<?>, Pair<Integer, Long>> streamIndexAndResetTimestamp
            = Maps.newHashMap();

    /**
     * Each metric is given an index for the life of this context. Indexes are assigned
     * (and this map is populated) when they're found.
     *
     * @see #getMetricIndex(GenericMetric, StreamDefinition.Builder)
     */
    private final Map<GenericMetric<?, ?>, Integer> metricIndices = Maps.newHashMap();

    private static final Logger LOG = Logger.getLogger(MonitorStreamsContext.class.getName());

    // Logger that backs off to FINER after the first message each second.
    // TODO(avrukin) Escalating Logger? WTF?
    // private static final EscalatingLogger RESET_TIMESTAMPS_MOVED_BACKWARDS = new
    //        EscalatingLogger(LOG, new ThrottlingEscalator(1000, 1, Level.FINER));

    // Set to true when the first response is being built. (Used to meet the protocol's
    // specification)
    private final AtomicBoolean sentResponses = new AtomicBoolean(false);

    MonitorStreamsContext(MonitorRequest monitorRequest) {
        this(monitorRequest, false);
    }

    MonitorStreamsContext(MonitorRequest monitorRequest,
                          boolean emptyStringMatchesAllMetrics) {
        this.request = monitorRequest;
        this.emptyStringMatchesAllMetrics = emptyStringMatchesAllMetrics;
    }

    /**
     * Used by testing.LocalMetricReader.
     */
    @VisibleForTesting
    public static MonitorEvent collectSingleRequest(MetricFactory factory, MonitorRequest request) {
        return new MonitorStreamsContext(request).collect(factory);
    }

    // Returns the small (zero-based) integer which will refer to the stream
    // specified by |cell| for the lifetime of this RequestContext.  If this
    // is the first time a given stream has been seen by this Context, its
    // StreamDefinition is emitted into |streamValue|.
    private int getStreamIndex(GenericCell<?> cell, StreamValue.Builder streamValue) {
        CellKey<?> cellKey = cell.getCellKey();
        Pair<Integer, Long> indexAndResetTimestamp = streamIndexAndResetTimestamp.get(cellKey);
        if (indexAndResetTimestamp != null) {
            if (indexAndResetTimestamp.second < cell.getResetTimestampMicros()) {
                // reset timestamp has advanced - generate a new index
                indexAndResetTimestamp = null;
            } else if (indexAndResetTimestamp.second > cell.getResetTimestampMicros()) {
                // TODO(avrukin) Fix this
                /* RESET_TIMESTAMPS_MOVED_BACKWARDS.warning(
                        "Streamz cell reset timestamp moving backwards: " + indexAndResetTimestamp.second
                                + " to " + cell.getResetTimestampMicros() + ". Cell=" + cell.toString()
                ); */
                return -1;
            }
        }
        if (indexAndResetTimestamp == null) {
            StreamDefinition.Builder streamDefinition = StreamDefinition.newBuilder();

            GenericMetric<?, ?> metric = cell.getOwner();
            streamDefinition.setMetricIndex(getMetricIndex(metric, streamDefinition));

            for (int ii = 0; ii < metric.getNumFields(); ii++) {
                Object fieldValue = cell.getField(ii);
                Class<?> fieldType = metric.getField(ii).getNativeType();
                if (fieldType == String.class) {
                    streamDefinition.addStringFieldValues(fieldValue.toString());
                } else if (fieldType == Integer.class) {
                    streamDefinition.addIntFieldValues((Integer) fieldValue);
                } else if (fieldType == Boolean.class) {
                    streamDefinition.addBoolFieldValues((Boolean) fieldValue);
                } else {
                    throw new IllegalStateException("Unexpected field type: " + fieldType);
                }
            }
            long reset = cell.getResetTimestampMicros();
            streamDefinition.setResetTimestamp(reset);
            streamValue.setStreamDefinition(streamDefinition);
            indexAndResetTimestamp = Pair.of(nextStreamIndex, reset);
            nextStreamIndex++;
            streamIndexAndResetTimestamp.put(cellKey, indexAndResetTimestamp);
        }
        return indexAndResetTimestamp.first;
    }

    // Returns the small (zero-based) integer which will refer to the metric
    // |metric| for the lifetime of this Context.  If this is the first time
    // a given metric has been seen by this Context, its MetricDefinition is
    // attached to |streamDefinition|.
    private int getMetricIndex(
            GenericMetric<?, ?> metric, StreamDefinition.Builder streamDefinition) {
        Integer index = metricIndices.get(metric);
        if (index == null) {
            streamDefinition.setMetricDefinition(
                    metric.getMetricDefinition(request.getIncludeDeclarationMetadata()));
            index = metricIndices.size();
            metricIndices.put(metric, index);
        }
        return index;
    }

    /**
     * Create a {@link MonitorEvent} for all metrics listed in the supplied {@link MonitorRequest}
     * that apply to the {@code factory}.
     */
    MonitorEvent collect(final MetricFactory factory) {
        final MonitorEvent.Builder event = MonitorEvent.newBuilder();
        Receiver<GenericMetric<Object, ?>> metricCallback = new Receiver<GenericMetric<Object, ?>>() {
            @Override
            public void accept(GenericMetric<Object, ?> metric) {
                if (metric.getMetadata().isForEyesOnly()) {
                    return;
                }
                try {
                    metric.applyToEachCell(new Receiver<GenericCell<Object>>() {
                        @Override public void accept(GenericCell<Object> cell) {
                            // Note, we repeatedly acquire/release the cells' locks,
                            // even if (as is usual) all cells share the same lock.
                            StreamValue.Builder streamValue = StreamValue.newBuilder();
                            streamValue.setStreamIndex(getStreamIndex(cell, streamValue));
                            cell.toStreamValue(streamValue, request.getTimestamps());
                            event.addValue(streamValue);
                        }
                    });
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            "Exception thrown during collection loop while applying to metric '"
                                    + metric.getName() + "'.", e);
                }
            }
        };

        List<String> patterns = Lists.newArrayList();
        for (StreamQuery query : request.getQueryList()) {
            if (!query.getPattern().isEmpty() || emptyStringMatchesAllMetrics) {
                patterns.add(query.getPattern());
            }
        }

        factory.applyToMetrics(patterns, metricCallback);
        event.setTimestampMicros(Utils.getCurrentTimeMicros());
        if (sentResponses.compareAndSet(false, true)) {
            event.setRootId(factory.getId());
        }
        return event.build();
    }

    @VisibleForTesting
    int getNextStreamIndex() {
        return nextStreamIndex;
    }
}