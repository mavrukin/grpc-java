// Generated by generate.py

package io.grpc.monitoring.streamz;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;

import javax.annotation.Generated;

/**
 * A five-dimensional EventMetric.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @param <F3> The type of the third metric field.
 * @param <F4> The type of the fourth metric field.
 * @param <F5> The type of the fifth metric field.
 * @author ecurran@google.com (Eoin Curran)
 */
@Generated(value = "generate.py")
public class EventMetric5<F1, F2, F3, F4, F5>
    extends EventMetricBase<EventMetric5<F1, F2, F3, F4, F5>> {
  private final Metric5<F1, F2, F3, F4, F5, Distribution> metric;

  EventMetric5(String name, Bucketer bucketer, Metadata metadata,
      Field<F1> field1,
      Field<F2> field2,
      Field<F3> field3,
      Field<F4> field4,
      Field<F5> field5) {
    super(bucketer);
    metric = new Metric5<F1, F2, F3, F4, F5, Distribution>(
        name, ValueTypeTraits.getTraits(Distribution.class), metadata.setCumulative(),
        ImmutableList.of(
            field1,
            field2,
            field3,
            field4,
            field5));
    metric.setDefaultValue(new Distribution(bucketer));
  }

  /**
   * Records the given value in the distribution.
   */
  public void record(
      Metric5.CellKey<F1, F2, F3, F4, F5> key,
      double value) {
    if (Distribution.isValidSample(value, 1)) {
      metric.changeUnderLock(key, createChanger(value));
    }
  }

  /**
   * @see #record(Metric5.CellKey<F1, F2, F3, F4, F5>, double)
   */
  public void record(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      double value) {
    record(Metric5.createCellKey(
        field1, field2, field3, field4, field5), value);
  }

  /**
   * Records the given value in the distribution with provided
   * com.google.protobuf.Any attachments. All provided attachments must
   * use different type URL.
   */
  public void recordWithExemplar(
      Metric5.CellKey<F1, F2, F3, F4, F5> key,
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
      F3 field3,
      F4 field4,
      F5 field5,
      double value, Any... attachments) {
    recordWithExemplar(Metric5.createCellKey(
        field1, field2, field3, field4, field5),
        value, attachments);
  }

  /**
   * Records the given value in the distribution, "count" times.
   */
  public void recordMultiple(
      Metric5.CellKey<F1, F2, F3, F4, F5> key,
      double value, long count) {
    if (Distribution.isValidSample(value, count)) {
      metric.changeUnderLock(key, createChanger(value, count));
    }
  }

  /**
   * @see #recordMultiple(Metric5.CellKey<F1, F2, F3, F4, F5>, double, long)
   */
  public void recordMultiple(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      double value, long count) {
    recordMultiple(Metric5.createCellKey(
        field1, field2, field3, field4, field5),
        value, count);
  }

  /**
   * Ensures a distribution is defined for the supplied fields. If there is
   * none, one is created with zero elements. If one already exists for the
   * supplied fields, there is no change.
   *
   * @return this object.
   */
  public EventMetric5<F1, F2, F3, F4, F5> forceCreate(
      Metric5.CellKey<F1, F2, F3, F4, F5> key) {
    metric.getOrCreateCell(key);
    return this;
  }

  /**
   * @see #forceCreate(Metric5.CellKey<F1, F2, F3, F4, F5>)
   */
  public EventMetric5<F1, F2, F3, F4, F5> forceCreate(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5) {
    return forceCreate(Metric5.createCellKey(
        field1, field2, field3, field4, field5));
  }

  /**
   * Returns a copy of the distribution for the specified cell.
   */
  public Distribution get(
      Metric5.CellKey<F1, F2, F3, F4, F5> key) {
    return metric.get(key);
  }

  /**
   * @see #get(Metric5.CellKey<F1, F2, F3, F4, F5>)
   */
  public Distribution get(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5) {
    return get(Metric5.createCellKey(
        field1, field2, field3, field4, field5));
  }

  @Override
  protected StoredMetric<Distribution, ?> getUnderlyingMetric() {
    return metric;
  }
}