package io.grpc.monitoring.streamz.utils;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.truth.Truth;
import java.util.Map;
import javax.annotation.CheckReturnValue;

/**
 * Keeps track of the value of a cumulative Long-valued metric. It can check that the value
 * has incremented by the correct amount in the amount in the correct cells.
 *
 * <p><b>Implementation note:</b> This class is marked as {@link Beta} which means the
 * class could change or be removed at any time. We are not quite certain how these testers
 * should be used and would rather find out <i>how</i> they are used, and modify or remove
 * these classes after the fact.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@Beta
public class CumulativeMetricTester {
  private final MetricReferenceTester<Long> metric;
  private Map<FieldKey, Long> baseline;

  CumulativeMetricTester(MetricReferenceTester<Long> metric) {
    this.metric = metric;
    recordBaseline();
  }

  private void recordBaseline() {
    this.baseline = metric.getValues();
  }

  /**
   * Returns a map containing an entry for each cell which has
   * changed. Missing cells are assumed to be 0-valued when calculating
   * deltas.
   */
  public Map<FieldKey, Long> getDeltasAndReset() {
    metric.assertCumulative();
    Map<FieldKey, Long> newValues = metric.getValues();
    Map<FieldKey, Long> deltas = Maps.newHashMap();
    // TODO(ecurran): respect reset timestamps
    for (FieldKey fieldKey : newValues.keySet()) {
      long newValue = newValues.get(fieldKey);
      long baselineValue = safe(baseline.get(fieldKey));
      long delta = newValue - baselineValue;
      if (delta != 0) {
        deltas.put(fieldKey, delta);
      }
    }
    for (FieldKey fieldKey : baseline.keySet()) {
      if (!newValues.containsKey(fieldKey)) {
        deltas.put(fieldKey, -baseline.get(fieldKey));
      }
    }
    this.baseline = newValues;
    return deltas;
  }

  /**
   * Asserts that exactly one cell incremented by the specified amount.
   * Resets the baseline.
   */
  public void assertIncrementalCount(long expectedDelta, Object... fieldValues) {
    assertIncrementalCount(null, expectedDelta, fieldValues);
  }

  /**
   * Asserts that exactly one cell incremented by the specified amount.
   * Resets the baseline.
   */
  public void assertIncrementalCount(String message, long expectedDelta, Object... fieldValues) {
    FieldKey fieldKey = metric.createFieldKey(fieldValues);
    assertIncrementalCount(message, expectedDelta, fieldKey);
  }

  /**
   * Asserts that exactly one cell incremented by the specified amount.
   * Fails if any other cell changed.
   * Resets the baseline.
   */
  void assertIncrementalCount(long expectedDelta, FieldKey fieldKey) {
    assertIncrementalCount(null, expectedDelta, fieldKey);
  }

  /**
   * Asserts that exactly one cell incremented by the specified amount.
   * Fails if any other cell changed.
   * Resets the baseline.
   */
  void assertIncrementalCount(String message, long expectedDelta, FieldKey fieldKey) {
    assertIncrementalCounts(message, ImmutableMap.of(fieldKey, expectedDelta));
  }

  /**
   * Asserts that exactly one cell incremented by one.
   * Resets the baseline.
   */
  // Note no corresponding method with a String message because it doesn't work
  // with varargs when the fieldValues are also Strings.
  public void assertIncrementByOne(Object... fieldValues) {
    assertIncrementalCount(null, 1, fieldValues);
  }

  /**
   * Asserts that no cells have changed.
   */
  public void assertNoChange() {
    assertNoChange(null);
  }

  /**
   * Asserts that no cells have changed.
   */
  public void assertNoChange(String message) {
    assertIncrementalCounts(message, ImmutableMap.<FieldKey, Long>of());
  }

  /**
   * Asserts which cells incremented and by how much.
   * Resets the baseline.
   */
  public void assertIncrementalCounts(Map<FieldKey, Long> expectedDeltaMap) {
    assertIncrementalCounts(null, expectedDeltaMap);
  }

  /**
   * Asserts which cells incremented and by how much.
   * Resets the baseline.
   */
  public void assertIncrementalCounts(String message, Map<FieldKey, Long> expectedDeltaMap) {
    if (!expectedDeltaMap.isEmpty()) {
      metric.assertHasDefinition();
    }
    if (message == null) {
      Truth.assertThat(getDeltasAndReset()).isEqualTo(expectedDeltaMap);
    } else {
      Truth.assertWithMessage(message).that(getDeltasAndReset()).isEqualTo(expectedDeltaMap);
    }
  }

  @CheckReturnValue
  public CounterChecker incrementalCountsAssertionChain() {
    return new CounterChecker(null);
  }

  @CheckReturnValue
  public CounterChecker incrementalCountsAssertionChain(String message) {
    return new CounterChecker(message);
  }

  /**
   * A helper class to check multiple counts.
   */
  public class CounterChecker {
    private final String message;
    private final ImmutableMap.Builder<FieldKey, Long> fieldCounts = ImmutableMap.builder();

    private CounterChecker(String message) {
      this.message = message;
    }

    /**
     * Sets an expectation for the specified field.
     */
    @CheckReturnValue
    public ExpectedDelta forKey(Object... fieldValues) {
      return new ExpectedDelta(metric.createFieldKey(fieldValues));
    }

    /**
     * Asserts that all checks added with {@link #forKey} have the expected delta.
     * Resets the baseline.
     */
    public void verify() {
      assertIncrementalCounts(message, fieldCounts.build());
    }

    /**
     * A helper class to check for an expected delta.
     */
    public class ExpectedDelta {
      private final FieldKey fieldKey;

      private ExpectedDelta(FieldKey fieldKey) {
        this.fieldKey = fieldKey;
      }

      /**
       * Verifies the key has the specified {@code expectedDelta}.
       */
      @CheckReturnValue
      public CounterChecker isEqualTo(long expectedDelta) {
        if (expectedDelta != 0) {
          fieldCounts.put(fieldKey, expectedDelta);
        }
        return CounterChecker.this;
      }
    }
  }

  /**
   * Converts null values to 0.
   */
  public static long safe(Long value) {
    return value != null ? value : 0L;
  }
}