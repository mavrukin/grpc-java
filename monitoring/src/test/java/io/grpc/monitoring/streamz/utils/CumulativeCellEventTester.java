package io.grpc.monitoring.streamz.utils;

import com.google.common.annotations.Beta;
import io.grpc.monitoring.streamz.Distribution;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.Arrays;

/**
 * Keeps track of the value of an EventMetric cell. Can be used to check that
 * the metric was updated correctly.
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
public class CumulativeCellEventTester {
  private final CellReference<Distribution> cell;
  private Distribution baseline;

  CumulativeCellEventTester(CellReference<Distribution> cell) {
    this.cell = cell;
    this.baseline = cell.getValue();
  }

  /**
   * Asserts that the distribution watched by this tester has a distribution
   * which matches the supplied values.
   */
  public void assertIncrementalEvents(double... values) {
    assertIncrementalEvents(null, values);
  }

  /**
   * Asserts that the distribution watched by this tester has a distribution
   * which matches the supplied values.
   */
  public void assertIncrementalEvents(String message, double... values) {
    Distribution newValue = cell.getValue();
    Distribution expected = null;
    if (baseline != null) {
      expected = baseline.copy();
    } else if (newValue != null) {
      expected = new Distribution(newValue.getBucketer());
    } else if (values.length != 0) {
      String header = ((message != null) ? (message ) : "");
      String formatted = String.format(
              "%s expected:<%s> but metric %s %s is not published", header, Arrays.toString(values),
              cell.getMetricReference().getMetricName(), cell.getFieldKey());
      throw new AssertionFailedError(formatted);
    }
    for (double value : values) {
      expected.add(value);
    }
    this.baseline = newValue;
    Assert.assertEquals(message, expected, newValue);
  }
}