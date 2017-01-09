// Generated by generate.py

package io.grpc.monitoring.streamz;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;

import javax.annotation.Generated;

/**
 * A two-dimensional EventMetric.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @author ecurran@google.com (Eoin Curran)
 */
@Generated(value = "generate.py")
public class EventMetric2<F1, F2>
    extends EventMetricBase<EventMetric2<F1, F2>> {
  private final Metric2<F1, F2, Distribution> metric;

  EventMetric2(String name, Bucketer bucketer, Metadata metadata,
      Field<F1> field1,
      Field<F2> field2) {
    super(bucketer);
    metric = new Metric2<F1, F2, Distribution>(
        name, ValueTypeTraits.getTraits(Distribution.class), metadata.setCumulative(),
        ImmutableList.of(
            field1,
            field2));
    metric.setDefaultValue(new Distribution(bucketer));
  }

  /**
   * Records the given value in the distribution.
   */
  public void record(
      Metric2.CellKey<F1, F2> key,
      double value) {
    if (Distribution.isValidSample(value, 1)) {
      metric.changeUnderLock(key, createChanger(value));
    }
  }

  /**
   * @see #record(Metric2.CellKey<F1, F2>, double)
   */
  public void record(
      F1 field1,
      F2 field2,
      double value) {
    record(Metric2.createCellKey(
        field1, field2), value);
  }

  /**
   * Records the given value in the distribution with provided
   * com.google.protobuf.Any attachments. All provided attachments must
   * use different type URL.
   */
  public void recordWithExemplar(
      Metric2.CellKey<F1, F2> key,
      double value, Any... attachments) {
    if (Distribution.isValidSample(value, 1)) {
      metric.changeUnderLock(key, createChanger(value, attachments));
    }
  }

  /**
   * Records the given value in the distribution with provided
   * com.google.protobuf.Any attachments. All provided attachments must
   * use different type URL.
   */
  public void recordWithExemplar(
      F1 field1,
      F2 field2,
      double value, Any... attachments) {
    recordWithExemplar(Metric2.createCellKey(
        field1, field2),
        value, attachments);
  }

  /**
   * Records the given value in the distribution, "count" times.
   */
  public void recordMultiple(
      Metric2.CellKey<F1, F2> key,
      double value, long count) {
    if (Distribution.isValidSample(value, count)) {
      metric.changeUnderLock(key, createChanger(value, count));
    }
  }

  /**
   * @see #recordMultiple(Metric2.CellKey<F1, F2>, double, long)
   */
  public void recordMultiple(
      F1 field1,
      F2 field2,
      double value, long count) {
    recordMultiple(Metric2.createCellKey(
        field1, field2),
        value, count);
  }

  /**
   * Ensures a distribution is defined for the supplied fields. If there is
   * none, one is created with zero elements. If one already exists for the
   * supplied fields, there is no change.
   *
   * @return this object.
   */
  public EventMetric2<F1, F2> forceCreate(
      Metric2.CellKey<F1, F2> key) {
    metric.getOrCreateCell(key);
    return this;
  }

  /**
   * @see #forceCreate(Metric2.CellKey<F1, F2>)
   */
  public EventMetric2<F1, F2> forceCreate(
      F1 field1,
      F2 field2) {
    return forceCreate(Metric2.createCellKey(
        field1, field2));
  }

  /**
   * Returns a copy of the distribution for the specified cell.
   */
  public Distribution get(
      Metric2.CellKey<F1, F2> key) {
    return metric.get(key);
  }

  /**
   * @see #get(Metric2.CellKey<F1, F2>)
   */
  public Distribution get(
      F1 field1,
      F2 field2) {
    return get(Metric2.createCellKey(
        field1, field2));
  }

  @Override
  protected StoredMetric<Distribution, ?> getUnderlyingMetric() {
    return metric;
  }
}