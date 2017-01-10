package io.grpc.monitoring.streamz;

import java.util.EnumMap;
import java.util.Map;

/**
 * Internal Streamz client library metrics.
 */
class StreamzMetrics {
    /**
     * Supported stored cell types.
     */
    private enum CellType {
        Long, Distribution, Integer, Boolean, Float, Double, String, Enum
    };

    /**
     * Number of stored cells of each type that are currently in use by the process.
     * Defined as a callback metric to avoid thinking about reentrance issues (since callback
     * metric does not use stored cells). And it is slightly faster since normal metric uses
     * ConcurrentHashMap to store cells.
     */
    static final CallbackMetric1<CellType, Long> numStoredCellsMetric =
            MetricFactory.getDefault()
                    .newCallbackMetric(
                            "/streamz/java/num_stored_cells",
                            Long.class,
                            new Metadata("Number of stored cell instances per class type"),
                            Field.ofEnumAsString(CellType.class, "type"))
                    .createTrigger(
                            new Runnable() {
                                @Override
                                public void run() {
                                    exportStoredCellCounts();
                                }
                            });

    private static final class Counter {
        long count = 0L;
        void add(long n) {
            count += n;
        }
        long get() {
            return count;
        }
    }

    /**
     * Export stored cell counts for all roots.
     */
    private static void exportStoredCellCounts() {
        EnumMap<CellType, Counter> counts = new EnumMap<CellType, Counter>(CellType.class);
        for (CellType type : CellType.values()) {
            counts.put(type, new Counter());
        }

        addStoredCellCountsForRoot(Root.getDefault(), counts);
        for (Root root : Root.getCustomRoots()) {
            addStoredCellCountsForRoot(root, counts);
        }
        for (Map.Entry<CellType, Counter> entry : counts.entrySet()) {
            numStoredCellsMetric.set(entry.getKey(), entry.getValue().get());
        }
    }

    private static void addStoredCellCountsForRoot(Root root, final Map<CellType, Counter> counts) {
        root.applyToMetricMetadata(new Receiver<GenericMetric<Object, ?>>() {
            @Override public void accept(GenericMetric<Object, ?> metric) {
                if (metric instanceof StoredMetric) {
                    int count = ((StoredMetric) metric).getCellCount();
                    Class<?> type = metric.getValueType();
                    if (type == Long.class) {
                        counts.get(CellType.Long).add(count);
                    } else if (type == Distribution.class) {
                        counts.get(CellType.Distribution).add(count);
                    } else if (type == Integer.class) {
                        counts.get(CellType.Integer).add(count);
                    } else if (type == Boolean.class) {
                        counts.get(CellType.Boolean).add(count);
                    } else if (type == Float.class) {
                        counts.get(CellType.Float).add(count);
                    } else if (type == Double.class) {
                        counts.get(CellType.Double).add(count);
                    } else if (type == String.class) {
                        counts.get(CellType.String).add(count);
                    } else if (type.isEnum()) {
                        counts.get(CellType.Enum).add(count);
                    }
                }
            }
        });
    }
}