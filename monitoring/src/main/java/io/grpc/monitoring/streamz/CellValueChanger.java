package io.grpc.monitoring.streamz;

import javax.annotation.Nullable;

/**
 * Callback interface to change the value of a cell in a metric.
 * See, e.g. {@link Metric0#changeUnderLock(CellValueChanger)}.
 * @param <V> the type of the value being changed
 */
public interface CellValueChanger<V> {
  /**
   * Changes the value of a cell. This method will be called with the Cell's
   * lock held.
   * @param currentValue The current value of the cell. This may be null
   *     if this invocation of change will initialize the cell's value and
   *     the metric has not had
   *     {@link GenericMetric#setDefaultValue(Object)} called.
   * @return The new value of the cell. If {@code V} is mutable, it is
   *     acceptable to mutate the value and return the same reference.
   *     Must not return null.
   */
  V change(@Nullable V currentValue);
}