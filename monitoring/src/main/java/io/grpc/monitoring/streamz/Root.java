package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.grpc.Status;
import io.grpc.monitoring.streamz.proto.RootDescriptor;
import io.grpc.monitoring.streamz.MetricFactory.RootMetricFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Root of the Streamz namespace.
 *
 * <p>The C++ API permits a single Streamz process to export multiple
 * namespaces with distinct roots.  In Java, the {@link #getDefault() default instance} is a
 * singleton and represents the metric Root for the JVM.
 * <p>
 * Please first talk to monarch-team before you use build custom roots. Not doing so may
 * cause you to export metrics in ways that overloads the discovery service with too many
 * announcements and additionally create monitoring anomalies that you'd find hard to debug!
 * <p>
 * This class is threadsafe.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class Root {
    private static final Root INSTANCE = new Root();

    private static ConcurrentMap<String, Root> customRoots = new ConcurrentHashMap<String, Root>();
    @Nullable
    private static RootLifecycleListener rootLifecycleListener;

    private final boolean DEFAULT_DUPLICATE_METRIC_NAMES = "strict".equals(System
            .getProperty("streamz.sanityChecks"));

    private final MetricRegistry delegate;
    private final MetricFactory metricFactory;
    private volatile RootDescriptor rootDescriptor;
    private volatile Long resetTimestampMicros;
    @Nullable private final String discoveryPrefix;

    @GuardedBy("this")
    private final List<Supplier<RootCollectionInfo>> collectionInfoSupplierList = new ArrayList<Supplier<RootCollectionInfo>>();

    @VisibleForTesting
    Root(RootDescriptor rootDescriptor, Long resetTimestampMicros, @Nullable String discoveryPrefix) {
        this.rootDescriptor = rootDescriptor;
        this.resetTimestampMicros = resetTimestampMicros;
        this.discoveryPrefix = discoveryPrefix;
        delegate = new MetricRegistry();
        delegate.setFailOnDuplicateMetricNames(DEFAULT_DUPLICATE_METRIC_NAMES);
        metricFactory = new RootMetricFactory(this);
    }

    private Root() {
        this(null, null, null);
    }

    @VisibleForTesting
    void setFailOnDuplicateMetricNames(boolean newValue) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.setFailOnDuplicateMetricNames(newValue);
    }

    @VisibleForTesting
    void restoreDefaultDuplicateNameBehavior() {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.setFailOnDuplicateMetricNames(DEFAULT_DUPLICATE_METRIC_NAMES);
    }

    void add(GenericMetric<?, ?> metric) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        if (resetTimestampMicros != null) {
            metric.setDefaultCellResetTimestamp(resetTimestampMicros);
        }
        delegate.add(metric);
    }

    /**
     * Applies the specified Receiver to the named metric, while holding the
     * namespace lock.  If the metric doesn't exist, a no-op.  {@code callback}
     * should not retain a reference to its argument once it completes.
     */
    void applyToMetric(String name, Receiver<GenericMetric<Object, ?>> callback) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.applyToMetric(name, callback);
    }

    /**
     * Applies the specified Receiver, while holding the namespace lock,
     * to all metrics whose names are "beneath" {@code directory},
     * according to the usual UNIX convention. {@code directory} must
     * have a trailing "/". {@code callback} should not retain a
     * reference to its argument once it completes.
     * <p>
     * The metrics are guaranteed to be passed to the {@code callback} in ascending
     * lexicographical order by their names.
     */
    void applyToMetricsBeneath(String directory,
                               Receiver<GenericMetric<Object, ?>> callback) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.applyToMetricsBeneath(directory, callback);
    }

    /**
     * Applies the specified Receiver, while holding the namespace lock, to all
     * metrics matching provided list of names. Names ending with trailing "/" (slash),
     * per usual UNIX conventions, will be considered to be {@code directory} names and
     * will rely on prefix matching instead.
     *
     * <p>{@code callback} should not retain a reference to its argument once it
     * completes.
     */
    void applyToMetrics(Iterable<String> names,
                        Receiver<GenericMetric<Object, ?>> callback) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.applyToMetrics(names, callback);
    }

    /**
     * For the purpose of reading only metric metadata, applies the specified Receiver to
     * all metrics while holding the namespace lock. No triggers are invoked before applying
     * the Receiver, and {@link GenericMetric#applyToEachCell} must not be called within
     * {@code callback}.
     * <p>
     * The metrics are guaranteed to be passed to the {@code callback} in ascending
     * lexicographical order by their names.
     *
     * <p>
     * {@code callback} should not retain a reference to its argument once it completes.
     */
    void applyToMetricMetadata(Receiver<GenericMetric<Object, ?>> callback) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.applyToMetricMetadata(callback);
    }

    /**
     * For the purpose of reading only metric metadata, applies the specified {@link Receiver} to
     * all metrics whose names are "beneath" {@code directory} according to the usual UNIX convention,
     * while holding the namespace lock.
     * <p>
     * No triggers are invoked before applying the receiver and {@link GenericMetric#applyToEachCell}
     * must not be called within {@code callback}. {@code callback} should not retain a
     * reference to its argument once it completes.
     * <p>
     * The metrics are guaranteed to be passed to the {@code callback} in ascending
     * lexicographical order by their names.
     *
     * @param directory directory that contains the metrics to be processed by the {@code callback}.
     *     {@code directory} must have a trailing "/" or be empty.
     * @param callback the receiver that will process the matching metrics.
     */
    void applyToMetricMetadataBeneath(String directory, Receiver<GenericMetric<Object, ?>> callback) {
        // Concurrency note: the delegate uses its object lock for read and write
        // operations on the metric set.
        delegate.applyToMetricMetadataBeneath(directory, callback);
    }

    final RootDescriptor getRootDescriptor() {
        if (rootDescriptor != null) {
            return rootDescriptor;
        } else {
            Preconditions.checkState(this == INSTANCE);
            return CoreMetrics.defaultRootBuilder().buildRootDescriptor();
        }
    }

    // The descriptor should only be built by Root.buildRootDescriptor().
    static final synchronized void initializeDefaultRootDescriptor(RootDescriptor descriptor) {
        Preconditions.checkState(INSTANCE.rootDescriptor == null, "Already initialized.");
        checkNotNull(descriptor);
        Preconditions.checkArgument(descriptor.getName().isEmpty(), "Default root name must be empty");
        INSTANCE.rootDescriptor = descriptor;
    }

    @Nullable
    String getDiscoveryPrefix() {
        return discoveryPrefix;
    }

    /**
     * Returns the default singleton {@link Root} instance.
     */
    public static Root getDefault() {
        return INSTANCE;
    }

    public String getName() {
        return getRootDescriptor().getName();
    }

    public MetricFactory getMetricFactory() {
        return metricFactory;
    }

    /**
     * Forces a manual push of all metrics associated with this root.
     *
     * @throws IllegalStateException if :manually_sampled_metrics is not linked.
     * @deprecated Use {@code StreamzClients.defaultClient().write(root)} instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public Future<Status> forceCollection() {
        // Really "return StreamzClients.defaultClient().write(this);".  Uses
        // reflection so that this can be built without linking in :client, which
        // will cause cyclic dependencies.
        //
        // TODO(chungyc): Replace uses of forceCollection() with StreamzClient.write().
        try {
            Class clientFactory = Class.forName("io.grpc.monitoring.streamz.StreamzClients");
            Method defaultClientMethod = clientFactory.getMethod("defaultClient");
            Object client = defaultClientMethod.invoke(null);
            Method writeMethod = client.getClass().getMethod("write", Root.class);
            return (Future<Status>) writeMethod.invoke(client, this);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "//java/com/google/monitoring/streamz:manually_sampled_metrics is not linked", e);
        }
    }

    /**
     * Please first talk to monarch-team before you use this builder for creating custom
     * {@link Root}s. Not doing so may cause you to export metrics in ways that overloads the
     * discovery service with too many announcements and additionally create monitoring anomalies
     * that you'd find hard to debug!
     * <p>
     * This should only be called if streamz_multi_root has been set.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for building custom {@link Root}s
     * <p>
     * Please first talk to monarch-team before you use this builder for creating custom
     * {@link Root}s. Not doing so may cause you to export metrics in ways that overloads the
     * discovery service with too many announcements and additionally create monitoring anomalies
     * that you'd find hard to debug!
     *
     * @author gnirodi@google.com (Gautam Nirodi)
     */
    public static class Builder {
        private String rootName = "";
        private String spatialContainer;
        private String spatialElement;
        private Long resetTimestamp;
        private String discoveryPrefix;
        private final List<StreamzRootLabel> streamzRootLabels;

        private Builder() {
            streamzRootLabels = Lists.newArrayList();
        }

        public Builder setSpatialContainer(String spatialContainer) {
            this.spatialContainer = spatialContainer;
            return this;
        }

        public Builder setSpatialElement(String spatialElement) {
            this.spatialElement = spatialElement;
            return this;
        }

        public Builder setRootName(String rootName) {
            this.rootName = rootName;
            return this;
        }

        /**
         * Allows custom roots implementors to set a root specific cell reset timestamp
         * for scenarios where the root lives outside the current process. If this {@link Root}
         * belongs to the current process this method ought not to be called. Implementors of
         * custom roots are required to track external root reset events and recreate the
         * {@link Root} by calling {@link #destroy} first and recreating the root. No attempt
         * will be made to accommodate {@link GenericMetric#invalidateNewCellResetTimestamp}.
         */
        public Builder setResetTimestamp(Long resetTimestamp) {
            this.resetTimestamp = resetTimestamp;
            return this;
        }

        public Builder addStreamzRootLabel(String name, String stringValue) {
            validateRootLabels(name);
            streamzRootLabels.add(new StreamzRootLabel(name, stringValue));
            return this;
        }

        public Builder addStreamzRootLabel(String name, Boolean boolValue) {
            validateRootLabels(name);
            streamzRootLabels.add(new StreamzRootLabel(name, boolValue));
            return this;
        }

        public Builder addStreamzRootLabel(String name, Long int64Value) {
            validateRootLabels(name);
            streamzRootLabels.add(new StreamzRootLabel(name, int64Value));
            return this;
        }

        public Builder setDiscoveryPrefix(String discoveryPrefix) {
            checkNotNull(discoveryPrefix, "Custom discovery prefix cannot be null");
            this.discoveryPrefix = discoveryPrefix;
            return this;
        }

        private void validateRootLabels(String name) {
            for (StreamzRootLabel label : streamzRootLabels) {
                Preconditions.checkArgument(!name.equals(label.getName()),
                        "Duplicate root labels with name '%s' prohibited", name);
            }
            checkNotNull(name, "Root label name cannot be null");
            Preconditions.checkArgument(!name.isEmpty(), "Root label name cannot be empty");
        }

        RootDescriptor buildRootDescriptor() {
            checkNotNull(rootName, "Custom root name cannot be null");
            Preconditions.checkArgument(streamzRootLabels.size() > 0, "No root labels defined");

            // If these are not null, they must not be empty.
            // They can be omitted (unless using Discovery), in which case they will be null.
            Preconditions.checkArgument(
                    spatialContainer == null || !spatialContainer.isEmpty(),
                    "Spatial container cannot be empty");
            Preconditions.checkArgument(
                    spatialElement == null || !spatialElement.isEmpty(),
                    "Spatial element cannot be empty");

            // Spatial container and element are required fields in RootDescriptor, but they can now be
            // optional.  Use the empty string (which cannot be a legal spatial container or element) to
            // signify that they have been omitted.
            RootDescriptor.Builder rootDescriptorBuilder = RootDescriptor.newBuilder()
                    .setName(rootName)
                    .setSpatialContainer(Strings.nullToEmpty(spatialContainer))
                    .setSpatialElement(Strings.nullToEmpty(spatialElement));

            for (StreamzRootLabel label : streamzRootLabels) {
                rootDescriptorBuilder.addLabels(label.makeStreamzAnnouncementLabel());
            }
            return rootDescriptorBuilder.build();
        }

        /**
         * Builds a {@link Root} from the spatial container, spatial element and root labels supplied
         * earlier to this {@link Builder} and announces the {@link Root} to Monarch.
         * <p>
         * Please first talk to monarch-team before you use build custom roots. Not doing so may
         * cause you to export metrics in ways that overloads the discovery service with too many
         * announcements and additionally create monitoring anomalies that you'd find hard to debug!
         */
        public Root build() {
            Preconditions.checkArgument(!rootName.isEmpty(), "Cannot override the default root.");
            RootDescriptor rootDescriptor = buildRootDescriptor();
            final Root customRoot = new Root(rootDescriptor, resetTimestamp, discoveryPrefix);
            customRoot.register();
            return customRoot;
        }
    }

    /**
     * Removes this {@link Root} from the list of custom roots registered with Monarch.
     */
    public void destroy() {
        Preconditions.checkArgument(this != Root.getDefault(), "Default Root cannot be unregistered.");
        Root registeredRoot = customRoots.remove(rootDescriptor.getName());
        checkNotNull(registeredRoot,
                "Root with name '%s' was never built using Root.Builder or has already been destroyed.",
                rootDescriptor.getName());
        if (rootLifecycleListener != null) {
            rootLifecycleListener.onUnregister(this);
        }
    }

    boolean isValid() {
        return customRoots.get(rootDescriptor.getName()) == this;
    }

    synchronized void registerCollectionInfoSupplier(
            Supplier<RootCollectionInfo> collectionInfoSupplier) {
        collectionInfoSupplierList.add(checkNotNull(collectionInfoSupplier));
    }

    synchronized void unregisterCollectionInfoSupplier(
            Supplier<RootCollectionInfo> collectionInfoSupplier) {
        collectionInfoSupplierList.remove(checkNotNull(collectionInfoSupplier));
    }

    synchronized List<RootCollectionInfo> getCollectionInfos() {
        ImmutableList.Builder<RootCollectionInfo> collectionInfos = ImmutableList.builder();
        for (Supplier<RootCollectionInfo> supplier : collectionInfoSupplierList) {
            collectionInfos.add(supplier.get());
        }
        return collectionInfos.build();
    }

    synchronized boolean isMonitoredWithNewProtocol() {
        return !collectionInfoSupplierList.isEmpty();
    }

    /**
     * Returns a previously built custom root by the supplied root name, or null if no such
     * Root exists.
     * <p>
     * This method is intended to be called only by the internal Streamz service.
     */
    @Nullable static Root find(String rootName) {
        return customRoots.get(rootName);
    }

    static List<Root> getCustomRoots() {
        return ImmutableList.copyOf(customRoots.values());
    }

    /**
     * Sets up a {@link RootLifecycleListener} that is notified of custom {@link Root} life cycle
     * events. This is only intended to be used by the Streamz service.
     */
    static void setLifecycleListener(RootLifecycleListener rootLifecycleListener) {
        Root.rootLifecycleListener = rootLifecycleListener;
    }

    private void register() {
        String rootName = rootDescriptor.getName();
        Preconditions.checkArgument(
                customRoots.putIfAbsent(rootName, this) == null,
                "Duplicate custom root creation prohibited: %s",
                rootName);
        if (rootLifecycleListener != null) {
            rootLifecycleListener.onRegister(this);
        }
    }
}
