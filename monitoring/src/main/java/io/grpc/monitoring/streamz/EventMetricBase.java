package io.grpc.monitoring.streamz;

import com.google.protobuf.Any;
import io.grpc.monitoring.streamz.proto.DistributionProto.Exemplar;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.annotation.concurrent.Immutable;

/**
 * Base class for EventMetrics, which are simple wrappers around a
 * Metric&lt;Distribution>.
 */
abstract class EventMetricBase<M extends EventMetricBase<M>> {
  private static final Logger logger = Logger.getLogger(EventMetricBase.class.getName());

  private final Bucketer bucketer;

  private static final long NO_TRACE_EXEMPLARS = -1L;
  private static final long MIN_TRACE_EXEMPLAR_INTERVAL_MILLIS = 20;

  /**
   *  Next TraceContext exemplar collection time (in millis since epoch).
   *  Setting it to NO_TRACE_EXEMPLARS will disable TraceContext exemplar collection.
   */
  private final AtomicLong nextTraceContextCollectionMillis = new AtomicLong(NO_TRACE_EXEMPLARS);

  EventMetricBase(Bucketer bucketer) {
    this.bucketer = bucketer;
  }

  /**
   * Enable collection of TraceContext exemplars when TraceContext is present during value
   * recording.
   */
  public M collectTraceExemplars() {
    nextTraceContextCollectionMillis.set(Utils.getClock().now().getMillis());
    return downcastThis();
  }

  @Override
  public String toString() {
    return "EventMetric(" + getName() + ")";
  }

  public String getName() {
    return getUnderlyingMetric().getName();
  }

  public List<String> getFieldNames() {
    return getUnderlyingMetric().getFieldNames();
  }

  @SuppressWarnings("unchecked")
  private M downcastThis() {
    return (M) this;
  }

  protected abstract StoredMetric<Distribution, ?> getUnderlyingMetric();

  /**
   * Records a value or multiple copies of the value in the specified bucket.
   */
  @Immutable
  static final class SimpleValueRecorder implements CellValueChanger<Distribution> {
    private final double value;
    private final int bucketId;

    SimpleValueRecorder(int bucketId, double value) {
      this.value = value;
      this.bucketId = bucketId;
    }

    @Override public Distribution change(Distribution distribution) {
      // StoredCell class guarantees that distribution value will never be null.
      distribution.addMultipleToBucket(bucketId, value, 1);
      return distribution;
    }
  }

  /**
   * Records multiple values with an optional exemplar in the specified bucket.
   */
  @Immutable
  static final class ValueRecorder implements CellValueChanger<Distribution> {
    private final double value;
    private final long count;
    private final int bucketId;
    private final Exemplar exemplar;

    ValueRecorder(int bucketId, double value, long count, Exemplar exemplar) {
      this.value = value;
      this.count = count;
      this.bucketId = bucketId;
      this.exemplar = exemplar;
    }

    @Override public Distribution change(Distribution distribution) {
      // StoredCell class guarantees that distribution value will never be null.
      distribution.addMultipleToBucket(bucketId, value, count);
      if (exemplar != null) {
        distribution.addExemplarToBucket(bucketId, exemplar);
      }
      return distribution;
    }
  }

  /**
   * Helper class to minimize access to clock during record() calls.
   */
  private static final class Now {
    private long nowMillis = -1L;

    /**
     * @return millis since epoch.
     */
    long getMillis() {
      if (nowMillis < 0L) {
        nowMillis = Utils.getClock().now().getMillis();
      }
      return nowMillis;
    }
  }

  /**
   * Returns true iff we should collect TraceContext exemplar.
   */
  private boolean collectTraceContext(Now now) {
    long nextMillis = nextTraceContextCollectionMillis.get();
    return (nextMillis != NO_TRACE_EXEMPLARS && now.getMillis() >= nextMillis);
  }

  /**
   * Creates TraceRecordId exemplar if there is an active trace context.
   *
   * @return true if exemplar was added, false - otherwise.
   */

  final CellValueChanger<Distribution> createChanger(double value) {
    Now now = new Now();
    if (collectTraceContext(now)) {
      Exemplar.Builder builder = Exemplar.newBuilder()
          .setValue(value)
          .setTimestamp(now.getMillis());
    }
    return new SimpleValueRecorder(bucketer.findBucket(value), value);
  }

  final CellValueChanger<Distribution> createChanger(double value, long count) {
    Now now = new Now();
    if (collectTraceContext(now)) {
      Exemplar.Builder builder = Exemplar.newBuilder()
          .setValue(value)
          .setTimestamp(now.getMillis());
    }
    return new ValueRecorder(bucketer.findBucket(value), value, count, null);
  }

  final CellValueChanger<Distribution> createChanger(double value, Any[] attachments) {
    Now now = new Now();
    Exemplar.Builder builder = Exemplar.newBuilder()
        .setValue(value)
        .setTimestamp(now.getMillis());
    boolean exemplarHasData = false;
    Set<String> types = new HashSet<String>();
    for (Any attachment : attachments) {
      // Each attachment type can be processed only once.
      if (types.add(attachment.getTypeUrl())) {
        builder.addAllExtraData(Arrays.asList(attachments));
        exemplarHasData = true;
      } else {
        logger.info("Couldn't attach exemplar to %s as the attachment %s was specified more than once.", this, attachment.getTypeUrl());
        logger.atInfo().atMostEvery(60, TimeUnit.SECONDS).log(
            "Couldn't attach exemplar to %s as the attachment %s was specified more than once.",
            this, attachment.getTypeUrl());
      }
    }
    
    if (exemplarHasData) {
      return new ValueRecorder(bucketer.findBucket(value), value, 1L, builder.build());
    }
    return new SimpleValueRecorder(bucketer.findBucket(value), value);
  }
}