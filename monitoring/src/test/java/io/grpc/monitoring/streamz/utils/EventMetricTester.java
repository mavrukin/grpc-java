package io.grpc.monitoring.streamz.utils;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import io.grpc.monitoring.streamz.Distribution;
import java.util.Collection;
import java.util.Map;
import javax.annotation.CheckReturnValue;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;

/**
 * Keeps track of the value of a cumulative Distribution-valued metric (usually, an EventMetric).
 * It can check that the Distribution has changed by the correct amount in the amount in the correct
 * cells.
 *
 * <p><b>Implementation note:</b> This class is marked as {@link Beta} which means the
 * class could change or be removed at any time. We are not quite certain how these testers
 * should be used and would rather find out <i>how</i> they are used, and modify or remove
 * these classes after the fact.
 *
 * @author reinerp@google.com (Reiner Pope)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@Beta
public class EventMetricTester {
  private final MetricReference<Distribution> metric;
  private Map<FieldKey, Distribution> baseline;

  EventMetricTester(MetricReference<Distribution> metric) {
    this.metric = metric;
    this.baseline = metric.getValues();
  }

  /**
   * Asserts that no cells were changed. Resets the baseline.
   */
  public void assertNoChange() {
    assertNoChange(null);
  }

  /**
   * Asserts that no cells were changed. Resets the baseline.
   */
  public void assertNoChange(String message) {
    assertRecordedMany(message, ImmutableMultimap.<FieldKey, Double>of());
  }

  /**
   * Asserts that matching cells were not changed. Resets the baseline.
   */
  public void assertNoChangeIgnoringOtherCells(FieldKeyMatcher matcher) {
    assertNoChangeIgnoringOtherCells(null, matcher);
  }

  /**
   * Asserts that matching cells were not changed. Resets the baseline.
   */
  public void assertNoChangeIgnoringOtherCells(String message, FieldKeyMatcher matcher) {
    Map<FieldKey, Distribution> currentValues = metric.getValues();
    for (Map.Entry<FieldKey, Distribution> currentEntry : currentValues.entrySet()) {
      if (!matcher.matches(currentEntry.getKey())) {
        continue;
      }
      Distribution expectedDistribution = baseline.get(currentEntry.getKey());
      if (expectedDistribution == null) {
        expectedDistribution = new Distribution(currentEntry.getValue().getBucketer());
      }
      checkDistributionsEqual(
          message, currentEntry.getKey(), expectedDistribution, currentEntry.getValue());
    }
    this.baseline = currentValues;
  }

  /**
   * Asserts that exactly one cell had exactly one value recorded, which was at the specified
   * amount. Resets the baseline.
   */
  public void assertRecordedOne(double value, Object... fieldValues) {
    assertRecordedOne(null, value, fieldValues);
  }

  /**
   * Asserts that the specified cell had exactly one value recorded of the specified amount.
   * Allows other cells to have changed as well. Resets the baseline.
   */
  public void assertRecordedOneIgnoringOtherCells(double value, Object... fieldValues) {
    assertRecordedOneIgnoringOtherCells(null, value, fieldValues);
  }

  /**
   * Asserts that exactly one cell had exactly one value recorded, regardless of the amount. Resets
   * the baseline.
   *
   * <p>If one of the objects that needs to be checked is a String, make sure to cast it to an
   * Object.
   */
  public void assertRecordedOneCell(Object... fieldValues) {
    assertRecordedOneCell(null, fieldValues);
  }

  /**
   * Asserts that exactly one cell had exactly one value recorded, which was at the specified
   * amount. Resets the baseline.
   */
  public void assertRecordedOne(String message, double value, Object... fieldValues) {
    assertRecordedMany(message, ImmutableMultimap.of(metric.createFieldKey(fieldValues), value));
  }

  /**
   * Asserts that the specified cell had exactly one value recorded of the specified amount.
   * Allows other cells to have changed as well. Resets the baseline.
   */
  public void assertRecordedOneIgnoringOtherCells(
      String message, double value, Object... fieldValues) {
    assertRecordedManyIgnoringOtherCells(message,
        ImmutableMultimap.of(metric.createFieldKey(fieldValues), value));
  }

  /**
   * Asserts that exactly one cell had exactly one value recorded, regardless of the amount. Resets
   * the baseline.
   *
   * <p>If one of the objects that needs to be checked is a String, make sure to cast it to an
   * Object.
   */
  public void assertRecordedOneCell(String message, Object... fieldValues) {
    assertRecordedManyCells(message, ImmutableMultiset.of(metric.createFieldKey(fieldValues)));
  }

  /**
   * Asserts that the specified cells have had the specified values recorded, and that no other
   * cells have changed. Resets the baseline.
   *
   * <b>Note:</b> To assist Java's type inference when constructing Multimaps, make sure all numbers
   * appear as type double or Double rather than type int or long. For example, write this:
   *
   * {@code ImmutableMultimap.of(FieldKey.of("a"), 2.0)}
   *
   * instead of this:
   *
   * {@code ImmutableMultimap.of(FieldKey.of("a"), 2)}
   */
  public void assertRecordedMany(Multimap<FieldKey, Double> expectedDeltaMap) {
    assertRecordedMany(null, expectedDeltaMap);
  }

  /**
   * Like {@link #assertRecordedMany}, except that it permits other cells to have changed.
   */
  public void assertRecordedManyIgnoringOtherCells(Multimap<FieldKey, Double> expectedDeltaMap) {
    assertRecordedManyIgnoringOtherCells(null, expectedDeltaMap);
  }

  /**
   * Like {@link #assertRecordedMany}, except that it only requires that some values were recorded
   * in the specified cells, without specifying what those values were.
   */
  public void assertRecordedManyCells(Multiset<FieldKey> expectedCells) {
    assertRecordedManyCells(null, expectedCells);
  }

  /**
   * Asserts that the specified cells have had the specified values recorded, and that no other
   * cells have changed. Resets the baseline.
   *
   * <b>Note:</b> To assist Java's type inference when constructing Multimaps, make sure all numbers
   * appear as type double or Double rather than type int or long. For example, write this:
   *
   * {@code ImmutableMultimap.of(FieldKey.of("a"), 2.0)}
   *
   * instead of this:
   *
   * {@code ImmutableMultimap.of(FieldKey.of("a"), 2)}
   */
  public void assertRecordedMany(String message, Multimap<FieldKey, Double> expectedDeltaMap) {
    Map<FieldKey, Distribution> currentValues = metric.getValues();
    Map<FieldKey, Distribution> expected = Maps.newHashMap();
    for (Map.Entry<FieldKey, Distribution> kv : baseline.entrySet()) {
      expected.put(kv.getKey(), kv.getValue().copy());
    }
    for (Map.Entry<FieldKey, Double> kv : expectedDeltaMap.entries()) {
      FieldKey fields = kv.getKey();
      double value = kv.getValue();
      Distribution dist = expected.get(fields);
      if (dist == null) {
        if (currentValues.containsKey(fields)) {
          dist = new Distribution(currentValues.get(fields).getBucketer());
          expected.put(fields, dist);
        } else {
          throw new AssertionFailedError(expectedToRecordMessage(message, currentValues, fields));
        }
      }
      dist.add(value);
    }

    this.baseline = currentValues;
    Assert.assertEquals(message, expected.keySet(), currentValues.keySet());
    for (FieldKey key : expected.keySet()) {
      checkDistributionsEqual(message, key, expected.get(key), currentValues.get(key));
    }
  }

  /**
   * Like {@link #assertRecordedMany}, except that it permits other cells to have changed.
   */
  public void assertRecordedManyIgnoringOtherCells(
      String message, Multimap<FieldKey, Double> expectedDeltaMap) {
    Map<FieldKey, Distribution> currentValues = metric.getValues();
    for (Map.Entry<FieldKey, Collection<Double>> expectedDelta :
        expectedDeltaMap.asMap().entrySet()) {
      FieldKey fieldKey = expectedDelta.getKey();
      if (!currentValues.containsKey(fieldKey)) {
        throw new AssertionFailedError(expectedToRecordMessage(message, currentValues, fieldKey));
      }
      Distribution expectedDistribution = baseline.get(fieldKey);
      if (expectedDistribution == null) {
        expectedDistribution = new Distribution(currentValues.get(fieldKey).getBucketer());
      } else {
        // Don't modify the baseline.
        expectedDistribution = expectedDistribution.copy();
      }
      for (Double value : expectedDelta.getValue()) {
        expectedDistribution.add(value);
      }
      checkDistributionsEqual(message, fieldKey, expectedDistribution, currentValues.get(fieldKey));
    }
    this.baseline = currentValues;
  }

  /**
   * Given a {@link Map} of {@link FieldKey} to {@link Distribution}, builds a {@link Multiset} of
   * every {@link FieldKey} recorded.
   */
  private Multiset<FieldKey> extractFieldKeyMultiset(Map<FieldKey, Distribution> values) {

    // Build a Multiset. Iterate over the entryset, so we get every occurrence of every FieldKey.
    Multiset<FieldKey> fieldMultisetToReturn = LinkedHashMultiset.create();
    for (Map.Entry<FieldKey, Distribution> kv : values.entrySet()) {

      // Add this FieldKey for every occurrence of a recorded distribution.
      fieldMultisetToReturn.add(kv.getKey(), (int) kv.getValue().getCount());
    }
    return fieldMultisetToReturn;
  }

  /**
   * Like {@link #assertRecordedMany}, except that it only requires that some values were recorded
   * in the specified cells, without specifying what those values were.
   */
  public void assertRecordedManyCells(String message, Multiset<FieldKey> expectedFields) {
    Multiset<FieldKey> currentFields = extractFieldKeyMultiset(metric.getValues());
    Multiset<FieldKey> baselineAndExpectedFields = extractFieldKeyMultiset(baseline);
    baselineAndExpectedFields.addAll(expectedFields);

    // Use a symmetric difference. Both sets need to match EXACTLY, but we want the difference for
    // logging the helpful information in the assertion failure. There is no
    // Multisets.symmetricDifference(), so build the symmetric difference by using each combination.
    Multiset<FieldKey> missingFromRecordedMetric = Multisets.difference(
        currentFields, baselineAndExpectedFields);
    Multiset<FieldKey> missingFromExpectation = Multisets.difference(
        baselineAndExpectedFields, currentFields);
    if (!missingFromRecordedMetric.isEmpty() || !missingFromExpectation.isEmpty()) {
      String header = ((message != null) ? (message + " ") : "");
      String baselineMiss = missingFromRecordedMetric.isEmpty() ? "" : " recorded this extra: "
          + missingFromRecordedMetric;
      String expectationMiss = missingFromExpectation.isEmpty() ? "" : " did not record this "
          + "expectation: "
          + missingFromExpectation;
      if (!baselineMiss.equals("")) {
        expectationMiss = " and" + expectationMiss;
      }
      throw new AssertionFailedError(header + "Metric " + metric.getMetricName()
          + baselineMiss + expectationMiss + ".");
    }
    this.baseline = metric.getValues();
  }


  @CheckReturnValue
  public EventChecker eventsAssertionChain() {
    return new EventChecker(null);
  }

  @CheckReturnValue
  public EventChecker eventsAssertionChain(String message) {
    return new EventChecker(message);
  }

  /**
   * A helper class to check multiple events.
   */
  public class EventChecker {
    private final String message;
    private final ImmutableMultimap.Builder<FieldKey, Double> fieldEvents =
        ImmutableMultimap.builder();

    private EventChecker(String message) {
      this.message = message;
    }

    /**
     * Sets an expectation for the specified field.
     */
    @CheckReturnValue
    public ExpectedRecord forKey(Object... fieldValues) {
      return new ExpectedRecord(metric.createFieldKey(fieldValues));
    }

    /**
     * Asserts that all checks added with {@link #forKey} have the expected amount.
     * Resets the baseline.
     */
    public void verify() {
      assertRecordedMany(message, fieldEvents.build());
    }

    /**
     * Asserts that all checks added with {@link #forKey} have the expected amount.
     * Allows other cells to have changed as well. Resets the baseline.
     */
    public void verifyIgnoringOtherCells() {
      assertRecordedManyIgnoringOtherCells(message, fieldEvents.build());
    }

    /**
     * A helper class to check for an expected value.
     */
    public class ExpectedRecord {
      private final FieldKey fieldKey;

      private ExpectedRecord(FieldKey fieldKey) {
        this.fieldKey = fieldKey;
      }

      /**
       * Verifies the key recorded the specified {@code expectedValue}.
       */
      @CheckReturnValue
      public EventChecker recorded(double... expectedValues) {
        for (double expectedValue : expectedValues) {
          fieldEvents.put(fieldKey, expectedValue);
        }
        return EventChecker.this;
      }
    }
  }

  /**
   * The calculations to do mean and variance are subject to floating point precision errors,
   * so allow one part per billion error to accumulate.
   */
  private static final double ALLOWED_PARTS_ERROR = 1e-9;

  private void checkDistributionsEqual(String message, FieldKey key, Distribution expected,
      Distribution found) {
    String preface = "";
    if (message != null) {
      preface = message + ": ";
    }
    preface += "for cell " + key + ": ";
    Assert.assertEquals(preface + "Distribution bucketing doesn't match.",
        expected.getBucketer(), found.getBucketer());
    Assert.assertEquals(preface + "Number of recorded values doesn't match.",
        expected.getCount(), found.getCount());
    Assert.assertEquals(preface + "Mean of recorded values doesn't match.",
        expected.getMean(), found.getMean(), expected.getMean() * ALLOWED_PARTS_ERROR);
    Assert.assertEquals(preface + "Variance of recorded values doesn't match.",
        expected.getVariance(), found.getVariance(), expected.getVariance() * ALLOWED_PARTS_ERROR);
    Assert.assertEquals(preface + "Number of values overflow values doesn't match.",
        expected.getOverflowCount(), found.getOverflowCount());
    Assert.assertEquals(preface + "Number of underflow values doesn't match.",
        expected.getUnderflowCount(), found.getUnderflowCount());
    for (int i = 0; i < expected.getBucketCount(); i++) {
      Assert.assertEquals(preface + "Number of values in bucket " + i + " doesn't match.",
          expected.getBucketHeight(i), found.getBucketHeight(i));
    }
  }

  private String expectedToRecordMessage(String message, Object currentValues, FieldKey fieldKey) {
    String header = ((message != null) ? (message + ": ") : "");
    return header + "expected to record on metric " + metric.getMetricName() + " with fields "
        + fieldKey + " but didn't. Other cells: " + currentValues;
  }
}