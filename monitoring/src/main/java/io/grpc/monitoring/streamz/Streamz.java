package io.grpc.monitoring.streamz;

import com.google.common.reflect.Reflection;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamz!
 *
 * @author konigsberg@google.com (Robert Konigsberg)
 */
public class Streamz {
    private static AtomicBoolean subsystemInitialized = new AtomicBoolean(false);

    /**
     * Declarative initialization of Streamz components.
     *
     * <p>The library is also intialized the first time a metric is created, so it's only
     * necessary to call if you are running without creating any metrics.
     *
     * <p>One way to identify if this needs to be called is if your binary does not have a
     * {@code /streamz} servlet.
     */
    public static void initialize() {
        StreamzInitializerToken.getMainToken().markInitialized();
        if (subsystemInitialized.compareAndSet(false, true)) {
            // Initialize Streamz metrics
            Reflection.initialize(StreamzMetrics.class);
            // Initialize core metrics
            Reflection.initialize(CoreMetrics.class);
            // Initialize /proc/jvm metrics
            if (!Utils.runningOnAppEngine()) {
                // JvmMetrics are not available on AppEngine.
                // TODO(avrukin + jeremymanson) - JVM Metrics
                // Reflection.initialize(JvmMetrics.class);
                // Initialize /streamz monitoring servlet hook
                // TODO(avrukin) do the actual servlet implementaiton
                // Reflection.initialize(StreamzServlet.class);
            }
            // Initialize /borg metrics
            // TODO(avrukin + jeremeymanson) - k8s / borglet metrics
            // Reflection.initialize(BorgletMetrics.class);
            // Initialize /build metrics
            // TODO(avrukin + jeremymanson) - Build Metrics
            // Reflection.initialize(BuildMetrics.class);

      /*
       * Export the rpc service and announce to discovery
       * If java/com/google/monitoring/streamz:streamz_service has been linked
       * into this binary, this will install the Streamz rpc service and announce
       * the process to discovery.
       */
            try {
                Class<?> streamzImplClass = Class.forName("io.grpc.monitoring.streamz.StreamzImpl");
                StreamzInitializerToken.getgRpcServiceToken().markInitialized();
            } catch (ClassNotFoundException e) {
        /* ignore */
            }
        }
    }
}