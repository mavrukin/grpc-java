package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.proto.StreamValue;
import io.grpc.monitoring.streamz.proto.Types.EncodedValueType;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A cell where we store the value and control access to it.
 *
 * @param <V> The type of data stored in the cell.
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@ThreadSafe
abstract class StoredCell<V> extends GenericCell<V> {
  private final CellKey<GenericMetric<V, ?>> key;
  protected final ValueTypeTraits<V> valueTypeTraits;

  /**
   * Reset timestamp for this cell.
   *
   * <p>If the owner metric is cumulative, then this value is the cell creation time in micros,
   * otherwise it's the owner's reset timestamp.
   * (We use a common reset time for non-cumulative metrics to increase sharing
   * of reset timestamp sequences in monitoring systems for streams that don't care about resets
   * because they are not cumulative.)
   *
   * @see Utils#getCurrentTimeMicros()
   */
  private final long resetTimestampMicros;

  /**
   * Constructs a cell.
   * @param owner  the owning Metric.
   * @param fieldTuple  the field-tuple identifying the location of this cell in the map, if any.
   *  Invariant: {@code fieldTuple.length() == getOwner().getNumFields().}
   *  the current time.
   */
  StoredCell(GenericMetric<V, ? extends GenericMetric<V, ?>> owner, FieldTuple fieldTuple) {
    this.key = new CellKey<GenericMetric<V, ?>>(owner, fieldTuple);
    this.resetTimestampMicros = owner.getMetadata().isCumulative()
        ? Utils.getCurrentTimeMicros() : owner.getNewCellResetTimestampMicros();
    this.valueTypeTraits = owner.getValueTypeTraits();
  }

  @Override
  final long getResetTimestampMicros() {
    return resetTimestampMicros;
  }

  /**
   * Returns the Metric that owns this Cell.
   */
  @Override
  GenericMetric<V, ? extends GenericMetric<V, ?>> getOwner() {
    return key.metric;
  }

  @Override
  final CellKey<?> getCellKey() {
    return key;
  }

  @Override
  final Object getField(int i) {
    return key.tuple.get(i);
  }

  @Override
  final void toStreamValue(StreamValue.Builder streamValue, boolean includeTimestamp) {
    if (hasValue()) {
      valueTypeTraits.toStreamValue(streamValue, getValue());
    }
  }

  /**
   * Sets the value of the cell to {@code value}.  Thread-safe.
   * The value is copied iff type V is mutable (e.g. Distribution).
   * {@code value} may not be null.
   */
  abstract void updateValue(V newValue);

  /**
   * Returns true if the cell has a value. A cell may not have a value after initialization.
   */
  boolean hasValue() {
    // Only enum types may not have a value for a short period of time. (immediately after cell
    // initialization).
    return (valueTypeTraits.getEncodedType() != EncodedValueType.VALUETYPE_ENUM
      || getValue() != null);
  }

  /**
   * Increments the value in this cell by one, atomically.  Only defined for
   * numeric types.  See {@link #incrementBy(Number)}.
   */
  final void increment() {
    incrementBy(1);
  }

  /**
   * Decrements the value in this cell by one, atomically.  Only defined for
   * numeric types.  See {@link #incrementBy(Number)}.
   */
  final void decrement() {
    incrementBy(-1);
  }

  /**
   * Increments the value in this cell by {@code step}, atomically.  Only
   * defined for numeric types. If {@code step} is of wider type or
   * higher precision than the cell, it will be narrowed or rounded before
   * adding.
   */
  abstract void incrementBy(Number step);

  /**
   * Invokes {@code changer.change(v)} in the thread-safe manner.
   * {@code v} is the Cell's value, and the changer should return the
   * new value. The changer should not retain the reference to {@code v}
   * after it has completed.
   *
   * <p>If the metric value is mutable, the changer could return the same
   * object after mutating it.
   *
   * @param changer will be called with the current value, and should return
   *     the new value. If {@code V} is mutable, the changer may mutate the
   *     object and return the same reference.
   * @pre current value is not null.
   * @post the new value is legal for the type (e.g. for a protocol message,
   *   all required fields are present).
   * @post the new value is not null.
   */
  abstract void changeUnderLock(CellValueChanger<V> changer);

  /**
   * Constructs a cell.
   * @param owner  the owning Metric.
   * @param fieldTuple  the field-tuple identifying the location of this cell in the map, if any.
   *  Invariant: {@code fieldTuple.length() == getOwner().getNumFields().}
   * @param value  the value with which to initialize this cell. Values may be null, which indicates
   *  an uninitialized cell.
   */
  static final <V> StoredCell<V> newStoredCell(
      GenericMetric<V, ? extends GenericMetric<V, ?>> owner,
      FieldTuple fieldTuple,
      V value) {
    if (owner.getValueTypeTraits().isConvertableToLong()) {
      return new AtomicLongCell<V>(owner, fieldTuple, value);
    } else if (owner.getValueType() == Distribution.class){
      return new DistributionCell<V>(owner, fieldTuple, value);
    } else {
      return new SynchronizedCell<V>(owner, fieldTuple, value);
    }
  }

  /**
   * Atomic cell used for numeric and boolean values.
   */
  private static final class AtomicLongCell<V> extends StoredCell<V> {

    /**
     * All values are stored as longs using AtomicLong instance.
     * Conversion of values is handled by {@link ValueTypeTraits} class.
     */
    private final AtomicLong holder;

    private AtomicLongCell(
        GenericMetric<V, ? extends GenericMetric<V, ?>> owner,
        FieldTuple fieldTuple,
        V value) {
      super(owner, fieldTuple);
      holder = new AtomicLong();
      owner.getValueTypeTraits().set(holder, value);
    }

    @Override
    V getValue() {
      return valueTypeTraits.fromLong(holder.get());
    }

    @Override
    void updateValue(V newValue) {
      Preconditions.checkNotNull(newValue,
          "You may not set a cell value to null. Remove the cell instead.");
      valueTypeTraits.set(holder, newValue);
    }

    @Override
    void incrementBy(Number step) {
      valueTypeTraits.incrementBy(holder, step);
    }

    @Override
    void changeUnderLock(CellValueChanger<V> changer) {
      long current, changed;
      do {
        current = holder.get();
        changed = valueTypeTraits.toLong(Preconditions.checkNotNull(
            changer.change(valueTypeTraits.fromLong(current)),
            "Cannot change value to null."));
      } while (!holder.compareAndSet(current, changed));
    }
  }


  /**
   * Cell for use with non-numeric, non-Distribution types.
   */
  private static final class SynchronizedCell<V> extends StoredCell<V> {

    /**
     * The current value of this cell.
     * Copying of values is handled by {@link ValueTypeTraits} class.
     */
    private V value;

    private SynchronizedCell(
        GenericMetric<V, ? extends GenericMetric<V, ?>> owner,
        FieldTuple fieldTuple,
        V value) {
      super(owner, fieldTuple);
      if (owner.getValueTypeTraits().getEncodedType() != EncodedValueType.VALUETYPE_VOID
          && owner.getValueTypeTraits().getEncodedType() != EncodedValueType.VALUETYPE_ENUM) {
        Preconditions.checkNotNull(value);
      }
      this.value = cloneValue(value);
    }

    // We must clone values that we set into or read out of a cell, to prevent
    // the caller's subsequent mutations via that reference from affecting the
    // cell.  For immutable types, clone is the identity function.  The caller
    // should already be holding the cell's monitor for mutual exclusion of
    // copying vs. mutation.
    private V cloneValue(V newValue) {
      return valueTypeTraits.clone(newValue);
    }

    @Override
    synchronized V getValue() {
      return cloneValue(value);
    }

    @Override
    synchronized void updateValue(V newValue) {
      if (valueTypeTraits.getEncodedType() != EncodedValueType.VALUETYPE_VOID) {
        Preconditions.checkNotNull(newValue,
            "You may not set a cell value to null. Remove the cell instead.");
      } else {
        Preconditions.checkArgument(newValue == null,
            "Void-valued metrics must only have null cells.");
      }
      this.value = cloneValue(newValue);
    }

    @Override
    synchronized void incrementBy(Number step) {
      throw new UnsupportedOperationException("incrementBy() not supported for " + valueTypeTraits);
    }

    @Override
    synchronized void changeUnderLock(CellValueChanger<V> changer) {
      this.value = Preconditions.checkNotNull(changer.change(value),
          "Cannot change value to null.");
    }
  }


  /**
   * Mostly synchronized-free cell for distribution values.
   */
  private static final class DistributionCell<V> extends StoredCell<V> {

    /** Number of available CPU cores */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The current value of this cell.
     * Copying of values is handled by {@link ValueTypeTraits} class.
     * All updates to this field is protected by the spin lock "lock".
     */
    private volatile Distribution distribution;

    /** Spin lock, protecting distribution updates. */
    private final AtomicBoolean lock = new AtomicBoolean(false);

    /** Cache offset pointing to the likely empty slot */
    private final AtomicInteger offset = new AtomicInteger(0);

    /**
     * Cache containing cell value changer instances that were not yet applied.
     * Cache is instantiated only when needed and can grow up to NCPU slots.
     */
    private final AtomicReference<AtomicReference<CellValueChanger<Distribution>>[]> cache =
       new AtomicReference<AtomicReference<CellValueChanger<Distribution>>[]>(null);

    private DistributionCell(
        GenericMetric<V, ? extends GenericMetric<V, ?>> owner,
        FieldTuple fieldTuple,
        V value) {
      super(owner, fieldTuple);
      Preconditions.checkArgument (value instanceof Distribution);
      Preconditions.checkNotNull(value);
      this.distribution = cloneValue((Distribution) value);
    }

    private Distribution cloneValue(Distribution newValue) {
      return newValue.copy();
    }

    @SuppressWarnings("unchecked")
    @Override
    V getValue() {
      try {
        // Obtain spin lock.
        // It is expected that getValue() will be called infrequently (usually it is used only when
        // collecting data to send to Monarch or to display on the Streamz page) - so there is no
        // need for a more complicated locking scheme.
        while (!lock.compareAndSet(false, true)) {}

        if (cache.get() != null) {
          // Apply all cached cell value changers and remove them from cache.
          // Note, that this will guarantee that all reported processed values (for which
          // changeUnderLock() has finished execution) will be applied before reading cell value.
          // However, there are no guarantees where it comes to the in-flight changeUnderLock()
          // calls - some of them may be applied during this loop and some are not. Which is
          // perfectly fine - we still guarantee that each update will be processed exactly once.
          for (AtomicReference<CellValueChanger<Distribution>> slot : cache.get()) {
            CellValueChanger<Distribution> changer = slot.getAndSet(null);
            if (changer != null) {
              if (changer.change(distribution) != distribution) {
                throw new IllegalStateException(
                    "Distribution instance must be mutated in-place for the cell " + this);
              }
            }
          }
        }
        return (V) cloneValue(distribution); // defensive copy.
      } finally {
        // Reset cache offset and free spin lock.
        offset.set(0);
        lock.set(false);
      }
    }

    @Override
    void updateValue(V newValue) {
      Preconditions.checkNotNull(newValue,
          "You may not set a cell value to null. Remove the cell instead.");
      Distribution value = cloneValue((Distribution) newValue);
      try {
        // Obtain spin lock.
        // It is expected that updateValue() for Distributions is called very infrequently - thus
        // there is no need for a more complicated locking scheme.
        while (!lock.compareAndSet(false, true)) {}

        if (cache.get() != null) {
          // Clear out any cached cell value changers.
          for (AtomicReference<CellValueChanger<Distribution>> slot : cache.get()) {
            slot.set(null);
          }
        }
        this.distribution = value;
      } finally {
        // Reset cache offset and free spin lock.
        offset.set(0);
        lock.set(false);
      }
    }

    @Override
    void incrementBy(Number step) {
      throw new UnsupportedOperationException("incrementBy() not supported for Distribution");
    }

    @SuppressWarnings("unchecked")
    @Override
    void changeUnderLock(CellValueChanger<V> changer) {
      // Perform explicit cast to the Distribution-valued cell changer.
      CellValueChanger<Distribution> newChanger = (CellValueChanger<Distribution>) changer;

      // Fast path - if we can obtain spin lock immediately, just apply update directly to the
      // distribution instance, bypassing cache.
      if (lock.compareAndSet(false, true)) {
        try {
          if (newChanger.change(distribution) != distribution) {
            throw new IllegalStateException(
                "Distribution instance must be mutated in-place for the cell " + this);
          }
        } finally {
          lock.set(false);
        }
        return;
      }

      // If cache exists, try to find empty slot. We will do only one pass over cache slots - once
      // offset exceeds cache lengths, caching will be effectively disabled until cache content is
      // applied.
      AtomicReference<CellValueChanger<Distribution>>[] slots = cache.get();
      if (slots != null && offset.get() < slots.length) {
        int pos;
        while ((pos = offset.getAndIncrement()) < slots.length) {
          if (slots[pos].compareAndSet(null, newChanger)) {
            // We stored changer in the cache. It will be applied later. Note that it is possible
            // for changers to be applied out of order. However, it is guaranteed that this change
            // will be applied before we return a cell value using getValue() method called after
            // this moment.
            return;
          }
        }
      }

      boolean tryGrowingCache;

      // Another spin lock attempt. If it is successful, we will flush cache (resizing it if needed)
      // and apply current change.
      if (lock.compareAndSet(false, true)) {
        try {
          tryGrowingCache = flushCache(slots);
          if (newChanger.change(distribution) != distribution) {
            throw new IllegalStateException(
                "Distribution instance must be mutated in-place for the cell " + this);
          }
        } finally {
          offset.set(0);
          lock.set(false);
        }
      } else {
        // Spin lock has failed. Most likely we are under heavy contention. Use synchronized block
        // (which is more effective under heavy contention than a spin lock) and only then
        // obtain spin lock and perform same operation as above.
        synchronized (this) {
          try {
            // Obtain spin lock.
            // We do not expect heavy contention here because majority of threads would be blocked
            // by the synchronized block.
            while (!lock.compareAndSet(false, true)) {}
            tryGrowingCache = flushCache(slots);
            if (newChanger.change(distribution) != distribution) {
              throw new IllegalStateException(
                  "Distribution instance must be mutated in-place for the cell " + this);
            }
          } finally {
            offset.set(0);
            lock.set(false);
          }
        }
      }

      // Grow cache if needed (and if it was not already grown by other thread).
      if (tryGrowingCache &&  slots == cache.get()) {
        growCache(slots);
      }
    }

    /**
     * Apply at least some of the cached value changers. We don't need to guarantee that all of
     * them are applied - this is needed only in the getValue() method.
     * <p>
     * This method must be called only under spin lock "lock" since it updates distribution value.
     *
     * @return true iff cache needs to be grown (because it was full or was not initialized).
     */
    private boolean flushCache(final AtomicReference<CellValueChanger<Distribution>>[] slots) {
      if (slots != null) {
        for (AtomicReference<CellValueChanger<Distribution>> slot : slots) {
          CellValueChanger<Distribution> changer = slot.getAndSet(null);
          if (changer != null) {
            if (changer.change(distribution) != distribution) {
              throw new IllegalStateException(
                  "Distribution instance must be mutated in-place for the cell " + this);
            }
          } else {
            // Stop flushing once we encountered an empty slot. It is possible that there are
            // some slots that still contain value (or changer was stored in them right at this
            // moment) but it is not important - they will just be flushed later.
            // Also, disable cache resizing since cache was not full.
            return false;
          }
        }
        // Request cache resizing if it has not reached maximum size (1 slot per CPU core).
        return (slots.length < NCPU);
      } else {
        // Request initializing cache if we have more than 1 CPU.
        return (NCPU > 1);
      }
    }

    /**
     * Grow changer cache if multiple CPU cores are available. Do not grow cache past
     * NCPU slots.
     */
    private void growCache(final AtomicReference<CellValueChanger<Distribution>>[] oldSlots) {
      int oldSize = (oldSlots != null ? oldSlots.length : 0);
      int newSize = (oldSize > 0 ? oldSize * 2 : 2);
      if (newSize > NCPU) {
        // Limit cache growth only up to 1 slot per available core.
        newSize = NCPU;
      }

      @SuppressWarnings("unchecked")
      AtomicReference<CellValueChanger<Distribution>>[] newSlots = new AtomicReference[newSize];
      if (oldSlots != null) {
        System.arraycopy(oldSlots, 0, newSlots, 0, oldSize);
      }
      for (int i = oldSize; i < newSize; i++) {
        newSlots[i] = new AtomicReference<CellValueChanger<Distribution>>(null);
      }

      // Try to resize cache. If we fail it means it was resized by other thread.
      cache.compareAndSet(oldSlots, newSlots);
    }
  }
}