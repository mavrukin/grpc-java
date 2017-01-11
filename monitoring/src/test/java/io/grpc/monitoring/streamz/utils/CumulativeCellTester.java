package io.grpc.monitoring.streamz.utils;

import com.google.common.annotations.Beta;
import junit.framework.Assert;

/**
 * Keeps track of the value of a cumulative Long-valued cell. It can
 * be used to check that the value has incremented by the correct
 * amount. If the cell is unset in the baseline and subsequently set,
 * the baseline version is taken as 0 for the purpose of calculating
 * deltas.
 *
 * <p>Example usage:
 *
 * <pre>
 *  Service service = ...; // service creates a cumulative metric named /my/metric.
 *
 *  StreamzTester st = new StreamzTester();
 *  CellReference<Long> cell = st.metricReference("/my/metric", Long.class).cell();
 *  CumulativeCellTester tester = cell.tester();
 *
 *  // expect service.operation to increment the cell's value by 1.
 *  service.operation();
 *  tester.assertIncrementalCount(1);
 *
 *  // expect service.otherOperation to increment the cell's value by 3.
 *  service.operation();
 *  tester.assertIncrementalCount(3);
 * </pre>
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
public class CumulativeCellTester {
  private final CellReferenceTester<Long> cell;
  private long baseline;

  CumulativeCellTester(CellReferenceTester<Long> cell) {
    this.cell = cell;
    this.baseline = safe(cell.getValue());
  }

  /**
   * Returns the amount the cell has increased since this object
   * was created or last reset.
   */
  public long getDelta() {
    cell.getMetricReference().assertCumulative();
    long newValue = safe(cell.getValue());
    long delta = newValue - baseline;
    return delta;
  }

  /**
   * Returns the amount the cell has increased since this object
   * was created or last reset. Resets the baseline in this object.
   */
  public long getDeltaAndReset() {
    cell.getMetricReference().assertCumulative();
    long newValue = safe(cell.getValue());
    long delta = newValue - baseline;
    this.baseline = newValue;
    return delta;
  }

  /**
   * Assert that the cell has not changed in value.
   */
  public void assertNoChange() {
    assertNoChange(null);
  }

  /**
   * Assert that the cell has not changed in value.
   */
  public void assertNoChange(String message) {
    assertIncrementalCount(message, 0);
  }

  /**
   * Asserts that the cell has increased by a certain amount
   * and resets the baseline.
   */
  public void assertIncrementalCount(long delta) {
    assertIncrementalCount(null, delta);
  }

  /**
   * Asserts that the cell has increased by a certain amount
   * and resets the baseline.
   */
  public void assertIncrementalCount(String message, long delta) {
    Assert.assertEquals(message, delta, getDeltaAndReset());
  }

  /**
   * Asserts that the cell has increased by one and resets
   * the baseline.
   */
  public void assertIncrementByOne() {
    assertIncrementByOne(null);
  }

  /**
   * Asserts that the cell has increased by one and resets
   * the baseline.
   */
  public void assertIncrementByOne(String message) {
    assertIncrementalCount(message, 1);
  }

  /**
   * Converts null values to 0.
   */
  public static long safe(Long value) {
    return value != null ? value : 0L;
  }
}