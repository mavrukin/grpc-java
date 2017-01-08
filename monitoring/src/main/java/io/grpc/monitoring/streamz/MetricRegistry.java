package io.grpc.monitoring.streamz;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * An internal registry of metrics. Used to track registration of metrics and
 * optionally supports duplicate metric names.
 *
 * This class is <b>thread safe</b>.
 *
 * @author jdb@google.com (Jon Blumenthal)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@ThreadSafe
final class MetricRegistry {
    private static final Logger logger = Logger.getLogger(MetricRegistry.class.getName());

    // private final AtomicBoolean failOnDuplcateMetricNames;

}