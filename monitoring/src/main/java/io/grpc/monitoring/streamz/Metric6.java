// Generated by generate.py

package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Set;

import javax.annotation.Generated;

/**
 * A six-dimensional Metric. This provides access to {@link StoredCell}s,
 * indexed by field values in a type-safe manner.
 *
 * <p>This class may only be instantiated through a {@link MetricFactory}.
 *
 * @param <F1> The type of the first metric field.
 * @param <F2> The type of the second metric field.
 * @param <F3> The type of the third metric field.
 * @param <F4> The type of the fourth metric field.
 * @param <F5> The type of the fifth metric field.
 * @param <F6> The type of the sixth metric field.
 * @param <V> The type of the values stored in metric cells.
 * @author ecurran@google.com (Eoin Curran)
 */
@Generated(value = "generate.py")
public class Metric6<F1, F2, F3, F4, F5, F6, V>
    extends StoredMetric<V, Metric6<F1, F2, F3, F4, F5, F6, V>> {

  /**
   * @see #createCellKey(
   *      Object, Object, Object, Object, Object, Object)
   */
  public static final class CellKey<F1, F2, F3, F4, F5, F6> extends FieldTuple {
    CellKey(F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
      super(field1, field2, field3, field4, field5, field6);
    }
  }

  /**
   * As a performance optimization, if you are updating a number of metrics or counters
   * that use the same set of fields, you can pre-create the cell key and
   * re-use it.
   */
  public static
      <F1, F2, F3, F4, F5, F6> CellKey<F1, F2, F3, F4, F5, F6>
      createCellKey(
          F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
    return new CellKey<F1, F2, F3, F4, F5, F6>(
        field1, field2, field3, field4, field5, field6);
  }

  Metric6(String name, ValueTypeTraits<V> traits, Metadata metadata,
      ImmutableList<? extends Field<?>> fields) {
    super(name, traits, metadata, fields);
    Preconditions.checkArgument(getNumFields() == 6);
  }

  /**
   * @see StoredCell#updateValue(Object)
   */
  public void set(
      CellKey<F1, F2, F3, F4, F5, F6> key,
      V value) {
    super.set(key, value);
  }

  /**
   * @see StoredCell#updateValue(Object)
   */
  public void set(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6,
      V value) {
    set(
        createCellKey(
            field1, field2, field3, field4, field5, field6),
        value);
  }

  /**
   * @see StoredCell#getValue()
   */
  public V get(CellKey<F1, F2, F3, F4, F5, F6> key) {
    return super.get(key);
  }

  /**
   * @see StoredCell#getValue()
   */
  public V get(F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
    return get(createCellKey(
        field1, field2, field3, field4, field5, field6));
  }

  /**
   * @see StoredMetric#clear(FieldTuple)
   */
  public void clear(CellKey<F1, F2, F3, F4, F5, F6> key) {
    super.clear(key);
  }

  /**
   * @see StoredMetric#clear(FieldTuple)
   */
  public void clear(F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
    clear(createCellKey(
        field1, field2, field3, field4, field5, field6));
  }

  /**
   * @see StoredCell#increment()
   */
  public void increment(CellKey<F1, F2, F3, F4, F5, F6> key) {
    super.increment(key);
  }

  /**
   * @see StoredCell#increment()
   */
  public void increment(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
    increment(createCellKey(
        field1, field2, field3, field4, field5, field6));
  }

  /**
   * @see StoredCell#decrement()
   */
  public void decrement(CellKey<F1, F2, F3, F4, F5, F6> key) {
    super.decrement(key);
  }

  /**
   * @see StoredCell#decrement()
   */
  public void decrement(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6) {
    decrement(createCellKey(
        field1, field2, field3, field4, field5, field6));
  }

  /**
   * @see StoredCell#incrementBy(Number)
   */
  public void incrementBy(
      CellKey<F1, F2, F3, F4, F5, F6> key, Number value) {
    super.incrementBy(key, value);
  }

  /**
   * @see StoredCell#incrementBy(Number)
   */
  public void incrementBy(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6, Number value) {
    incrementBy(
        createCellKey(
            field1, field2, field3, field4, field5, field6),
        value);
  }

  /**
   * @see StoredCell#changeUnderLock(CellValueChanger)
   */
  public void changeUnderLock(
      F1 field1,
      F2 field2,
      F3 field3,
      F4 field4,
      F5 field5,
      F6 field6,
      CellValueChanger<V> valueChanger) {
    changeUnderLock(
        createCellKey(
            field1, field2, field3, field4, field5, field6),
        valueChanger);
  }

  /**
   * @see StoredCell#changeUnderLock(CellValueChanger)
   */
  public void changeUnderLock(
      CellKey<F1, F2, F3, F4, F5, F6> key,
      CellValueChanger<V> valueChanger) {
    super.changeUnderLock(key, valueChanger);
  }

  /**
   * @see StoredMetric#keySet()
   */
  // We ensure that only FieldTuples created by createCellKey are used as keys in the map
  @SuppressWarnings("unchecked")
  @Override
  public Set<CellKey<F1, F2, F3, F4, F5, F6>> keySet() {
    return (Set<CellKey<F1, F2, F3, F4, F5, F6>>) super.keySet();
  }
}