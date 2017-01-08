package io.grpc.monitoring.streamz;

import com.google.common.collect.ImmutableList;

/**
 * A metric whose values are defined entirely by callback.
 *
 * <p>Metrics cannot be created directly. See {@link MetricFactory} for factory
 * methods for VirtualMetrics.
 *
 * <p>VirtualMetrics with no fields use the {@link
 * MetricFactory#newMetric(String, Class, Metadata, com.google.common.base.Supplier)}
 * constructor, which uses a {@code Supplier} to return the single cell
 * value, for example:
 *
 * <pre><code>
 * new Supplier&lt;Long&gt;() {
 *   &#64;Override
 *   public Long get() {
 *      return Runtime.freeMemory();
 *    }};
 * </code></pre>
 *
 * <p>VirtualMetrics with multiple fields have a similar API but use a
 * {@link com.google.common.base.Receiver} to define values. A defining function
 * for a {@code VirtualMetric<VT, F1, ..., Fn>} looks like this:
 *
 * <pre>{@code
 * void accept(DefineCellCallback<F1, F2, V> callback) {
 *   for (each logical cell in this metric) {
 *     callback.defineCell(field1, field2, value);
 *   }
 * }
 * }</pre>
 *
 * <h3>When to use:</h3>
 *
 * <p>Use VirtualMetric when the values of your exported metric are
 * already maintained by your application, but you don't wish to
 * duplicate them inside a metric, e.g. to save space, to avoid
 * calling into Streamz code on your application's fast path, or
 * perhaps because your application simply doesn't know when the
 * set of map fields and their corresponding values change.
 *
 * <p>VirtualMetric provides a GenericMetric "view" onto your application's
 * datastructure.  The view is only evaluated when an RPC or HTTP
 * request is made.
 *
 * <h3>Example usage:</h3>
 *
 * <p>In this example, a VirtualMetric provides a view onto the mounted
 * filesystem table, which is maintained by the operating system and
 * may change without the application's knowledge.
 *
 * <pre><code>
 * VirtualMetric1&lt;String, Double&gt; diskUsage = MetricFactory.getDefault().newMetric(
 *     "/filesystems/disk_usage",
 *     Double.class,
 *     new Metadata("Per-device disk usage as a fraction of the total.")
 *         .setUnit("second"),
 *     String.class, "device",
 *     new Receiver&lt;DefineCellCallback&lt;String, Double&gt;&gt;() {
 *       &#64;Override public void accept(
 *           &#64;Nullable VirtualMetric1.DefineCellCallback&lt;String, Double&gt; callback) {
 *       for (PartitionInfo pinfo : getAllPartitionInfo()) {
 *         callback.defineCell(pinfo.getName(), pinfo.getFreeRatio());
 *       }
 *     }});
 * </code></pre>
 *
 * <p>This next example demonstrates the implementation of a zero-dimensional
 * VirtualMetric that uses a Supplier to return the single cell's value.
 *
 * <pre><code>
 * private final VirtualMetric0&lt;Long&gt; uptime = MetricFactory.getDefault().newMetric(
 *     "/proc/uptime",
 *     Long.class,
 *     uptimeMetadata,
 *     new Supplier&lt;Long&gt;() {
 *       &#64;Override
 *       public Long get() {
 *         return ManagementFactory.getRuntimeMXBean().getUptime();
 *     }});
 * </code></pre>
 *
 * <h3>Internal notes:</h3>
 *
 * <p>Instances of virtual metric do not use StoredCells to store logical map
 * cells.  (Cells are only used transiently during
 * {@link GenericMetric#applyToEachCell(com.google.common.base.Receiver)}.)
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
abstract class VirtualMetric<V, M extends VirtualMetric<V, M>> extends GenericMetric<V, M> {
    VirtualMetric(
            String name, ValueTypeTraits<V> traits, Metadata metadata,
            ImmutableList<? extends Field<?>> fields) {
        super(name, traits, metadata, fields);
        // The reset timestamp should be initialized to metric creation time.
        getNewCellResetTimestampMicros();
    }
}