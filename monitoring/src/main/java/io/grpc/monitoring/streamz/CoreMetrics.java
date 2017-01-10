package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.sun.management.UnixOperatingSystemMXBean;
import io.grpc.monitoring.streamz.proto.RootDescriptor;
import io.grpc.monitoring.streamz.proto.StreamzAnnouncement.Label;
import io.grpc.monitoring.streamz.Metadata.Units;
import io.grpc.monitoring.streamz.VirtualMetric1.DefineCellCallback;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A singleton holding the process's "/proc" metrics, which export information
 * about the process itself, used by monitoring systems for monitoring.
 *
 * <p>A note about concurrency and visibility: because this class is package private,
 * all access to the class is tightly controlled. This itself makes the class thread-safe.
 * Exposing some of the API such that streamzRootLabels was mutated further would violate
 * the thread-safety.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
enum CoreMetrics {
    instance; // (singleton)

    private static final Logger logger = Logger.getLogger(CoreMetrics.class.getName());

    private static final Root.Builder defaultRootBuilder = Root.newBuilder();

    private static final AtomicBoolean flagsParsed = new AtomicBoolean(false);

    static Root.Builder defaultRootBuilder() {
        return defaultRootBuilder;
    }

    // TODO(konigsberg): Metadata description improvement?
    private static final Metadata ROOT_LABEL_METADATA = new Metadata(
            "Exported root label. See google3/monitoring/streamz/public/root.h for " +
                    "more information about root labels.");

    @VisibleForTesting
    static void addConstantMetricsForRootLabels(MetricFactory factory, RootDescriptor descriptor) {
        for (Label label : descriptor.getLabelsList()) {
            String name = label.getName();

            // Partition each root label type into a namespace to avoid metadata conflict.
            if (label.getValueCase().getNumber() == Label.STRING_VALUE_FIELD_NUMBER) {
                factory.newConstant(
                        "/streamz/root_labels/string/" + name,
                        label.getStringValue(), ROOT_LABEL_METADATA);
            } else if (label.getValueCase().getNumber() == Label.INT64_VALUE_FIELD_NUMBER) {
                factory.newConstant(
                        "/streamz/root_labels/int64/" + name,
                        label.getInt64Value(), ROOT_LABEL_METADATA);
            } else if (label.getValueCase().getNumber() == Label.BOOL_VALUE_FIELD_NUMBER) {
                factory.newConstant(
                        "/streamz/root_labels/bool/" + name,
                        label.getBoolValue(), ROOT_LABEL_METADATA);
            } else {
                throw new IllegalStateException(
                        "Unexpected type for label: " + label + ", " + label.getClass());
            }
        }
    }

    // If true, use JNI to get process utime/stime.
    @VisibleForTesting
    static volatile boolean useResourceUsageTime = true;

    /**
     * Returns pair (cpu user time, cpu system time) of the current process, in seconds.
     */
    private static Pair<Double, Double> getProcessTime() {
        // TODO(avrukin + jeremymanson) Machine Stats
        /*if (useResourceUsageTime) {
            SystemResourceUsage sysResourceUsage =
                    SystemResourceUsage.getResourceUsage(SystemResourceUsage.Who.SELF);
            if (sysResourceUsage != null) {
                // The cpu time in getrusage() is in microseconds.
                return Pair.of(1E-6 * sysResourceUsage.utime, 1E-6 * sysResourceUsage.stime);
            }
            // Once getting resource usage fails once,
            // fail back to reading /proc/self/stat
            useResourceUsageTime = false;
        }
        Proc.Stat stat = Proc.getSafeStat("self");
        // The cpu time in proc/self/stat is specified in jiffies, i.e. 1/100 second.
        return Pair.of(0.01 * stat.utime, 0.01 * stat.stime); */
        return Pair.of(0.0d, 0.0d);
    }

    @SuppressWarnings("unused") // Constant metrics can be untouched.
    private final ConstantMetric<Boolean> found = MetricFactory.getDefault().newConstant(
            "/presence/found",
            true,
            new Metadata("A trivial, always-true metric that implicitly indicates when the associated " +
                    "target could be found and collected from."));

    private final Metadata birthMetadata = new Metadata(
            "The time at which this process started, in microseconds since the UNIX epoch")
            .setUnit(Units.MICROSECONDS);
    @SuppressWarnings("unused") // Constant metrics can be untouched.
    private final ConstantMetric<Long> birthTimestamp = MetricFactory.getDefault().newConstant(
            "/proc/birth_timestamp",
            Utils.getProcessStartTimeMicros(),
            birthMetadata);

    private static void exportUptime() {
        Metadata uptimeMetadata = new Metadata(
                "The uptime of this process in milliseconds").setUnit(Units.MILLISECONDS);
        MetricFactory.getDefault().newMetric(
                "/proc/uptime",
                Long.class,
                uptimeMetadata,
                new Supplier<Long>() {
                    @Override public Long get() {
                        return ManagementFactory.getRuntimeMXBean().getUptime();
                    }});
    }

    private static void exportHeapSize() {
        Metadata heapMetadata = new Metadata(
                "The current heap size, in bytes").setUnit(Units.BYTES);
        MetricFactory.getDefault().newMetric(
                "/proc/memory/heap_size",
                Long.class,
                heapMetadata,
                new Supplier<Long>() {
                    @Override public Long get() {
                        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                    }});
    }

    private static void exportDeprecatedCpuUsage() {
        // TODO(kbrady): change this metric to deprecated when all three language versions of
        //               /proc/cpu/usage are submitted. It is inaccurate and problematic.
        Metadata deprecatedCpuMetadata = new Metadata(
                "CPU Time consumed by this process, in seconds").setCumulative().setUnit(Units.SECONDS);
        // TODO(avrukin) Debug what he's complaining about
        /* MetricFactory.getDefault().newMetric(
                "/proc/cpu_usage",
                Double.class,
                deprecatedCpuMetadata,
                Field.ofString("mode"),
                new Receiver<DefineCellCallback<String, Double>>() {
                    @Override public void accept(DefineCellCallback<String, Double> callback) {
                        Pair<Double, Double> processTime = getProcessTime();
                        callback.defineCell("user", processTime.getFirst());
                        callback.defineCell("system", processTime.getSecond());
                    }
                }); */
    }

    // See http://b/3410593#comment18 for discussion of why rusage is inaccurate for cputime
    // measurements. This metric matches the C++ and Go ones in using /proc/self/cputime_ns which
    // solves the problem.
    private static double getCpuUsage() {
        // TODO(avrukin + jeremymanson) CPU Stats
        // return Proc.getProcess("self").getCpuUsage() / 1E9;
        return 0.0d;
    }

    private static void exportCpuUsage() {
        final Metadata cpuMetadata = new Metadata("Total CPU time consumed by this process.")
                .setCumulative().setUnit(Units.SECONDS);
        final CallbackMetric0<Double> cpuUsage =
                MetricFactory.getDefault().newCallbackMetric(
                        "/proc/cpu/usage", Double.class, cpuMetadata
                );

        MetricFactory.getDefault().newTrigger(
                cpuUsage, new Runnable() {
                    @Override
                    public void run() {
                        cpuUsage.set(getCpuUsage());
                    }
                }
        );
    }

    private static void exportMXBeanCpuUsage() {
        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

      // TODO(avrukin + jeremymanson) doing a quick implementation that will work
      // getSystemLoadAverage gives us a % based average over the previous minute
      // we can thus multiply this value by 60 to get the amount of "seconds" that were
      // consumed during the previous minute.  If the load is say 0.25 then it would mean we
      // consumed 15 seconds worth of CPU time over the previous minute, if it was 2.0 (two cores
      // at 100%) that would mean we consumed 120 seconds worth of CPU time.
        /* if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            final com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) osBean;

            final Metadata cpuMetadata = new Metadata("Total CPU time consumed by this process.")
                    .setCumulative().setUnit(Units.SECONDS);
            final CallbackMetric0<Double> cpuUsage =
                    MetricFactory.getDefault().newCallbackMetric(
                            "/proc/cpu/usage", Double.class, cpuMetadata
                    );

            MetricFactory.getDefault().newTrigger(
                    cpuUsage, new Runnable() {
                        @Override
                        public void run() {
                            cpuUsage.set(os.getProcessCpuTime() / 1E9);
                        }
                    }
            );
        } */

      final Metadata cpuMetadata = new Metadata("Total CPU time consumed by this process.")
          .setCumulative().setUnit(Units.SECONDS);
      final CallbackMetric0<Double> cpuUsage = MetricFactory.getDefault().newCallbackMetric(
          "/proc/cpu/usage", Double.class, cpuMetadata);
      MetricFactory.getDefault().newTrigger(cpuUsage, new Runnable() {
        @Override
        public void run() {
          cpuUsage.set(osBean.getSystemLoadAverage() * 60);
        }
      });
    }

    private static void exportGcuUsage() {
        /*
            TODO(avrukin + jeremeymanson) CPU utilization
        BorgletInfo borglet = new BorgletInfo(new BorgletEnvImpl());
        final double gcusPerCPU = borglet.getGcuScalingFactor();
        if (gcusPerCPU != 0) {
            Metadata gcuMetadata = new Metadata("Total GCU time consumed by this process.")
                    .setCumulative().setUnit(Units.SECONDS);

            final CallbackMetric0<Double> gcuUsage =
                    MetricFactory.getDefault().newCallbackMetric(
                            "/proc/gcu/usage", Double.class, gcuMetadata
                    );

            MetricFactory.getDefault().newTrigger(
                    gcuUsage, new Runnable() {
                        @Override
                        public void run() {
                            gcuUsage.set(getCpuUsage() * gcusPerCPU);
                        }
                    }
            );
            gcuUsage.exportToVarz("process-gcu-seconds");
        } else {
            if (borglet.isRunningUnderBorglet()) {
                logger.info("[streamz] No GCU scaling factor, so /proc/gcu/usage "
                        + "and process-gcu-seconds won't be exported.");
            }
        }
        */
    }

    private final Metadata hostnameMetadata = new Metadata(
            "The name of the host on which this process runs (not necessarily FQDN)");
    @SuppressWarnings("unused") // Constant metrics can be untouched.
    private final ConstantMetric<String> hostname = MetricFactory.getDefault().newConstant(
            "/proc/hostname",
            Utils.getHostname(),
            hostnameMetadata);

    private final Metadata gpidMetadata = new Metadata(
            "This process's global process id (for true global uniqueness, consult hostname too)");

    /*
        TODO(avrukin + jeremymanson) Environment Metrics
    @SuppressWarnings("unused") // Constant metrics can be untouched.
    private final ConstantMetric<Long> gpid = MetricFactory.getDefault().newConstant(
            "/proc/global_pid",
            GlobalProcessId.get(),
            gpidMetadata);
     */

    // TODO(bhs): Add /build metrics in Java, then deprecate this one.
    @SuppressWarnings("unused") // Constant metrics can be untouched.
    private final ConstantMetric<String> binaryName = MetricFactory.getDefault().newConstant(
            "/self/monarch_fields/binary_name",
            Utils.getMainClassName(),
            new Metadata("The name of this process's executable or main class"));

    private static void exportFileDescriptorCount() {
      // TODO(avrukin + jeremymanson) No good way to hack this up, just not available in the generic
      // version, will need a proper implementation when we go to prod
      /*
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean) {
            final UnixOperatingSystemMXBean unixOs = ((UnixOperatingSystemMXBean) os);

            final CallbackMetric0<Long> openFds = MetricFactory.getDefault().newCallbackMetric(
                    "/proc/num_open_fds",
                    Long.class,
                    new Metadata("Number of open file descriptors")
            );

            MetricFactory.getDefault().newTrigger(
                    openFds,
                    new Runnable() {

                        @Override
                        public void run() {
                            openFds.set(unixOs.getOpenFileDescriptorCount());
                        }
                    }
            );
        }
      */
    }

    // TODO(avrukin + jeremymanson) BORGLET STUFF!!!
    @VisibleForTesting
    static void initializeDefaultRootBuilder(
            Root.Builder builder, String hostname, String username /*, BorgletEnv borgletEnv*/) {
        builder.setRootName("");

        // Set spatial container and element.
        String spatialElement;
        // BorgletInfo borgletInfo = new BorgletInfo(borgletEnv);
        // if ((spatialElement = Utils.getCorpLocation(hostname)) != null) {
        //     builder.setSpatialContainer("corp_site"); // e.g. ("corp_site", "nyc")
        //    builder.setSpatialElement(spatialElement);
        // } else if ((spatialElement = Utils.getProdCluster(hostname)) != null) {
        //    builder.setSpatialContainer("cluster"); // e.g. ("cluster", "yp")
        //    if (borgletInfo.isRunningUnderBorglet()
        //            && borgletInfo.getBorgCell().length() >= 2) {
                // Pick the first two letters of the borg cell over the first two letters of the hostname
                // per the comments in monitoring/streamz/internal/root.cc.
        //        builder.setSpatialElement(borgletInfo.getBorgCell().substring(0, 2));
        //    } else {
        //        builder.setSpatialElement(spatialElement);
        //    }
        // } else {
            builder.setSpatialContainer("unknown-ip"); // e.g. ("unknown-ip", "1.2.3.4")
            builder.setSpatialElement(hostname);
        // }

        // Add standard labels.

        // builder.addStreamzRootLabel("binary_name", Utils.getMainClassName());
        // builder.addStreamzRootLabel("global_pid", GlobalProcessId.get());
        builder.addStreamzRootLabel("hostname", hostname);

        if (username != null) {
            builder.addStreamzRootLabel("unix_user", username);
        }

        // if (borgletInfo.isRunningUnderBorglet()) {
        //    if (borgletInfo.getBorgCell() != null) {
        //        builder.addStreamzRootLabel("borg_cell", borgletInfo.getBorgCell());
        //    }
        //    if (borgletInfo.getUserName() != null) {
        //        builder.addStreamzRootLabel("borg_user", borgletInfo.getUserName());
        //    }
        //    if (borgletInfo.getJobName() != null) {
        //        builder.addStreamzRootLabel("borg_job", borgletInfo.getJobName());
        //        builder.addStreamzRootLabel("borg_task_num", (long) borgletInfo.getTaskId());
        //    }
        //    if (borgletInfo.getContainerName() != null) {
        //        builder.addStreamzRootLabel("container_name", borgletInfo.getContainerName());
        //    }
        // }
        // String corpCampus = Utils.getCorpLocation(hostname);
        // if (corpCampus != null) {
        //    builder.addStreamzRootLabel("corp_campus", corpCampus);
        // }
    }

    static {
        String username = System.getProperty("user.name");
        initializeDefaultRootBuilder(
                defaultRootBuilder, Utils.getHostname(), username /*, new BorgletEnvImpl()*/);

        if (username != null) {
            MetricFactory.getDefault().newConstant(
                    "/proc/unix_user",
                    username,
                    new Metadata("The UNIX userid of the owner of this process"));
        }

        if (!Utils.runningOnAppEngine()) {
            // ManagementFactory is unavailable on AppEngine.
            exportFileDescriptorCount();
            exportUptime();
            exportHeapSize();

            if ("Mac OS X".equals(System.getProperty("os.name"))) {
                // Mac doesn't have a /proc/self.
                exportMXBeanCpuUsage();
            } else {
                // /proc/self cannot be read on App Engine.
                exportCpuUsage();
                exportGcuUsage();
                // Neither SystemResourceUsage nor /proc/self work on App Engine.
                exportDeprecatedCpuUsage();
            }

            // Parsing the --streamz_default_root_labels flag interferes with the initialization in
            // DefaultsInitializer.  This will cause an attempted double-initialization if the flags are
            // parsed manually on App Engine (e.g. using AppEngineFlags).
            // TODO(phst): Try to get this working on App Engine.
            // DO NOT MOVE THIS to CoreMetricsFlags.
            // TODO(avrukin) handle startup flag registration
            /* Flags.registerCompletionHook(new Runnable() {
                @Override
                public void run() {
                    if (flagsParsed.getAndSet(true)) {
                        return;  // Flags already parsed.
                    }

                    // TODO(avrukin) handle flag parsing and configuration
                    // CoreMetricsFlags.parseStreamzRootLabelsFlag(defaultRootBuilder);
                    RootDescriptor descriptor = defaultRootBuilder.buildRootDescriptor();
                    addConstantMetricsForRootLabels(MetricFactory.getDefault(), descriptor);
                    Root.initializeDefaultRootDescriptor(descriptor);
                }
            }); */
        }
    }

}