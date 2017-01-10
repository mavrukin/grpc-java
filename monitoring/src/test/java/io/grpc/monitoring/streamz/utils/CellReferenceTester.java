package io.grpc.monitoring.streamz.utils;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.Distribution;

@Beta
public class CellReferenceTester<V> extends CellReference<V> {
  private final MetricReferenceTester<V> metric;

  CellReferenceTester(MetricReferenceTester<V> metric, FieldKey fieldKey) {
    super(metric, fieldKey);
    this.metric = Preconditions.checkNotNull(metric);
  }

  /**
   * Return a new {@link CumulativeCellTester} for this reference.
   */
  public CumulativeCellTester cumulativeTester() {
    Preconditions.checkState(metric.getValueType() == Long.class);
    metric.assertCumulative();
    @SuppressWarnings("unchecked")
    CellReference<Long> longCellReference = (CellReference<Long>) this;
    return new CumulativeCellTester(longCellReference);
  }

  /**
   * Return a new {@link CumulativeCellEventTester} for this reference.
   */
  public CumulativeCellEventTester cumulativeEventTester() {
    Preconditions.checkState(metric.getValueType() == Distribution.class);
    @SuppressWarnings("unchecked")
    CellReference<Distribution> distributionCellReference = (CellReference<Distribution>) this;
    return new CumulativeCellEventTester(distributionCellReference);
  }
}