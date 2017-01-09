package io.grpc.monitoring.streamz;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * Base class for metrics where Streamz stores the value of each cell.
 * Concrete subclasses have public methods that provide static type
 * checking for the number and type of fields.
 *
 * <p>For metrics supplied by application callback, see {@link VirtualMetric}.
 *
 * @see MetricFactory for factory methods.
 *
 * @param <V> The type of the metric value.
 * @param <M> The type of the concrete Metric subclass.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class StoredMetric<V, M extends StoredMetric<V, M>> extends GenericMetric<V, M> {
  private final ConcurrentMap<FieldTuple, StoredCell<V>> map =
      new ConcurrentHashMap<FieldTuple, StoredCell<V>>();

  StoredMetric(String name, ValueTypeTraits<V> traits, Metadata metadata,
      ImmutableList<? extends Field<?>> fields) {
    super(name, traits, metadata, fields);
  }

  @Override
  void applyToEachCell(Receiver<GenericCell<V>> callback) {
    for (StoredCell<V> cell : map.values()) {  // no locking, weakly-consistent iterator.
      if (cell.hasValue()) {
        callback.accept(cell);
      }
    }
  }

  /**
   * Returns number of cells in the metric.
   * Used by the /streamz/java/num_stored_cells metric.
   */
  int getCellCount() {
    return map.size();
  }

  StoredCell<V> createCell(FieldTuple fieldTuple, V initialValue) {
    // We assume that CHM.get() has returned null and this is the reason why this method is called.
    verifyFieldTuple(fieldTuple);
    StoredCell<V> newCell = StoredCell.newStoredCell(this, fieldTuple, initialValue);
    StoredCell<V> oldCell = map.putIfAbsent(fieldTuple, newCell);
    // If another thread inserted a value between get() and putIfAbsent(), use it.
    return oldCell != null ? oldCell : newCell;
  }

  StoredCell<V> getOrCreateCell(FieldTuple fieldTuple) {
    StoredCell<V> cell = map.get(fieldTuple);
    return cell != null ? cell : createCell(fieldTuple, getDefaultValue());
  }

  StoredCell<V> getOrCreateCell(FieldTuple fieldTuple, V initialValue) {
    StoredCell<V> cell = map.get(fieldTuple);
    return cell != null ? cell : createCell(fieldTuple, initialValue);
  }

  /**
   * Removes all cells of this metric.
   */
  public void clear() {
    invalidateNewCellResetTimestamp();
    map.clear();
  }

  void clear(FieldTuple fieldTuple) {
    invalidateNewCellResetTimestamp();
    map.remove(fieldTuple);
  }

  /**
   * Gets the current value but will not create the cell if it does not already
   * exist.
   * @return The value, or null if the cell does not exist.
   */
  @Nullable
  V get(FieldTuple tuple) {
    StoredCell<V> cell = map.get(tuple);
    return cell == null ? null : cell.getValue();
  }

  /**
   * See the note on {@link Metric1#createCellKey(Object)}. This method
   * is public for a gRPC streamz performance optimization.
   * Use the {@code set()} method on the MetricN subclass instead.
   * TODO(avrukin) validate this still needs to be public w/ gRPC
   */
  public void set(FieldTuple tuple, V value) {
    // This may create the cell, so we ensure the initial value is correct in that case.
    // i.e. the cell never exists with the default value in that case.
    getOrCreateCell(tuple, value).updateValue(value);
  }

  /**
   * See the note on {@link Metric1#createCellKey(Object)}. This method
   * is public for a gRPC streamz performance optimization.
   * Use the {@code increment()} method on the MetricN subclass instead.
   * TODO(avrukin) validate this still needs to be public w/ gRPC
   */
  public void increment(FieldTuple tuple) {
    getOrCreateCell(tuple).increment();
  }

  void decrement(FieldTuple tuple) {
    getOrCreateCell(tuple).decrement();
  }

  /**
   * See the note on {@link Metric1#createCellKey(Object)}. This method
   * is public for a gRPC streamz performance optimization.
   * Use the {@code incrementBy()} method on the MetricN subclass instead.
   * TODO(avrukin) validate this still needs to be public w/ gRPC
   */
  public void incrementBy(FieldTuple tuple, Number value) {
    getOrCreateCell(tuple).incrementBy(value);
  }

  /**
   * See the note on {@link Metric1#createCellKey(Object)}. This method
   * is public for a gRPC streamz performance optimization.
   * Use the {@code changeUnderLock()} method on the MetricN subclass instead.
   * TODO(avrukin) validate this still needs to be public w/ gRPC
   */
  public void changeUnderLock(FieldTuple tuple, CellValueChanger<V> changer) {
    // if the default value is null and the changer throws an exception or
    // returns null, the cell can be left with a value of null.
    getOrCreateCell(tuple).changeUnderLock(changer);
  }

  /**
   * Returns the set of keys that have been used to create cells in this metric.  Note
   * that, as streamz metrics are frequently changed concurrently, {@code get()} may be null
   * even if called with keys from this set.
   */
  Set<? extends FieldTuple> keySet() {
    return ImmutableSet.copyOf(map.keySet());
  }

  FieldTuple createFieldTuple(Object... fields) {
    return new FieldTuple(fields);
  }
}