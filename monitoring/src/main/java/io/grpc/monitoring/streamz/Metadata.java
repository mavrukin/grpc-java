package io.grpc.monitoring.streamz;

import com.google.common.collect.Maps;
import java.util.Map;

/**
 * Static metric metadata which is specified at Streamz metric construction
 * time. Used by the factory methods in {@link MetricFactory}.
 *
 * <p>Example usage:
 *
 * <pre>
 *   Counter0 counter = MetricFactory.getDefault().newCounter(
 *       "/myarea/myapp/request_bytes",
 *       new Metadata("Total bytes seen in myapp requests")
 *           .setPublic()
 *           .setUnit(Metadata.Units.BYTES));
 * </pre>
 *
 * There's more information about Streamz metadata
 * <a href="http://goto.google.com/streamz-metadata">here</a>.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class Metadata {
 /**
   * @see #setConstant()
   */
  public static final String CONSTANT = "CONSTANT";

  /**
   * @see #setCumulative()
   */
  public static final String CUMULATIVE = "CUMULATIVE";

  /**
   * @see #setDenominatorMetric(GenericMetric)
   */
  public static final String DENOMINATOR_METRIC = "DENOMINATOR_METRIC";

  /**
   * @see #setDeprecation(String)
   */
  public static final String DEPRECATION = "DEPRECATION";

  /**
   * @see #Metadata(String)
   */
  public static final String DESCRIPTION = "DESCRIPTION";

  /**
   * @see #setForEyesOnly()
   */
  public static final String FOR_EYES_ONLY = "FOR_EYES_ONLY";

  /**
   * @see #setGauge()
   */
  public static final String GAUGE = "GAUGE";

  /**
   * @see #setPublic()
   */
  public static final String PUBLIC = "PUBLIC";

  /**
   * @see #setUnit(String)
   */
  public static final String UNIT = "UNIT";

  /**
   * Names of certain well-known units.
   * This list is not meant to be exhaustive, but covers many common units and
   * it is recommended that these be used in place of raw strings.
   *
   * @see #setUnit(String)
   */
  public static class Units {
    public static final String SECONDS = "seconds";
    public static final String MILLISECONDS = "milliseconds";
    public static final String MICROSECONDS = "microseconds";
    public static final String NANOSECONDS = "nanoseconds";

    public static final String BITS = "bits";
    public static final String BYTES = "bytes";

    /** 1000 bytes (not 1024). */
    public static final String KILOBYTES = "kilobytes";
    /** 1e6 (1,000,000) bytes. */
    public static final String MEGABYTES = "megabytes";
    /** 1e9 (1,000,000,000) bytes. */
    public static final String GIGABYTES = "gigabytes";

    /** 1024 bytes. */
    public static final String KIBIBYTES = "kibibytes";
    /** 1024^2 (1,048,576) bytes. */
    public static final String MEBIBYTES = "mebibytes";
    /** 1024^3 (1,073,741,824) bytes. */
    public static final String GIBIBYTES = "gibibytes";

    private Units() { }
  }

  private static final String TRUE_VAL = "true";

  private final Map<String, String> annotations = Maps.newHashMap();
  private boolean forEyesOnly = false;

  /**
   * Create a new Metadata object.
   * @param description a textual description of what this metric represents, for example,
   * "Number of health checks the RPC client library has performed".
   */
  public Metadata(String description) {
    annotate(DESCRIPTION, description);
  }

  /**
   * Make a copy of the existing metadata.
   */
  Metadata(Metadata source) {
    annotations.putAll(source.annotations);
  }

  private Metadata annotate(String name, String value) {
    annotations.put(name, value);
    return this;
  }

  private Metadata appendAnnotation(String name, String value) {
    String original = annotations.get(name);
    if (original == null || original.isEmpty()) {
      annotations.put(name, value);
    } else {
      annotations.put(name, original + "\n" + value);
    }
    return this;
  }

  /**
   * Sets the "CONSTANT" metric annotation, which means that the value of cells
   * in the metric will not change over time.
   */
  public Metadata setConstant() {
    return annotate(CONSTANT, TRUE_VAL);
  }

  /**
   * Sets the "CUMULATIVE" metric annotation, which tells the monitoring system
   * that the cell values for this metric are cumulative; for example, all
   * counters are cumulative. This allows the monitoring system to correctly
   * compute time derivatives across restart boundaries for the monitored
   * target.
   *
   * <p>In particular, note that simple rate calculations of Streamz metrics
   * <em>not</em> marked as cumulative (note that metrics created with
   * {@link MetricFactory#newCounter} are always cumulative) will yield
   * occasional negative-valued spikes upon process restart.
   */
  public Metadata setCumulative() {
    return annotate(CUMULATIVE, TRUE_VAL);
  }

  /**
   * Names another Metric which can be used as a denominator for ratio
   * computations. The canonical example is the ratio of errors to
   * requests:
   *
   * <pre>
   *   Counter0 requests = factory.newCounter(
   *       "/myapp/requests",
   *       new Metadata("requests"));
   *
   *   // Implicitly the numerator metric.
   *   Counter0 errors = factory.newCounter(
   *       "/myapp/errors",
   *       new Metadata("errors").setDenominatorMetric(requests));
   * </pre>
   *
   * <p>Note that denominator metric annotations are strictly advisory and are
   * only to be used as hints for frontends; they will not
   * <em>automatically</em> induce ratio computations in Streamz,
   *
   * @see #setDenominatorMetric(String)
   */
  /* public Metadata setDenominatorMetric(GenericMetric<?, ?> denominatorMetric) {
    return setDenominatorMetric(denominatorMetric.getName());
  } */

  /**
   * Names another Metric which can be used as a denominator for ratio
   * computations. The canonical example is the ratio of errors to
   * requests:
   *
   * <pre>
   *   Counter0 requests = factory.newCounter(
   *       "/myapp/requests",
   *       new Metadata("requests"));
   *
   *   // Implicitly the numerator metric.
   *   Counter0 errors = factory.newCounter(
   *       "/myapp/errors",
   *       new Metadata("errors").setDenominatorMetric("/myapp/requests"));
   * </pre>
   *
   * <p>Note that DENOMINATOR_METRIC annotations are strictly advisory and are
   * only to be used as hints for frontends; they will not
   * <em>automatically</em> induce ratio computations in Streamz,
   *
   * @see #setDenominatorMetric(GenericMetric) for more details.
   */
  public Metadata setDenominatorMetric(String denominatorMetricName) {
    return annotate(DENOMINATOR_METRIC, denominatorMetricName);
  }

  /**
   * Sets the "DEPRECATION" metric annotation, setting the annotation value to
   * the {@code explanation} string which should be a brief explanation of why
   * the metric has been deprecated, perhaps with a pointer to an alternative
   * as appropriate.
   */
  public Metadata setDeprecation(String explanation) {
    return annotate(DEPRECATION, explanation);
  }

  /**
   * Sets the "FOR_EYES_ONLY" metric annotation, which tells the monitoring
   * system not to collect/store the given metric: it is for human consumption
   * (from the /streamz servlet, presumably) only.
   */
  public Metadata setForEyesOnly() {
    forEyesOnly = true;
    return annotate(FOR_EYES_ONLY, TRUE_VAL);
  }

  /**
   * Sets the "GAUGE" metric annotation, which tells the monitoring system that
   * the cell values for this metric are gauges. A "gauge" can be thought of as
   * a numerical value which has a meaningful zero point (unlike a counter) and
   * which is not a process-local statistic (again, like a count or an average
   * over a process lifetime). For instance, a cell measuring temperature is a
   * gauge. A constant cell is not. A cumulative cell is never a gauge.
   * In-process rates may be thought of as gauges, but note that such rates are
   * discouraged in the Streamz user's guide (which recommends that time
   * derivatives be computed outside of the process).
   */
  public Metadata setGauge() {
    return annotate(GAUGE, TRUE_VAL);
  }

  /**
   * Sets the "PUBLIC" metric annotation, which indicates to all system
   * developers that they may rely on the stability of the semantics for the
   * given metric (or that they will be given plenty of warning for any future
   * migrations away from it). Developers should not depend on non-public
   * metrics exported by other teams (i.e., in other areas of the Streamz
   * metric namespace).
   */
  public Metadata setPublic() {
    return annotate(PUBLIC, TRUE_VAL);
  }

  /**
   * Sets the "UNIT" metric annotation to the given {@code unitName}. This
   * unit name is not intended to affect the semantics of computations, but it
   * may be used to label axes and query results in downstream monitoring
   * systems.
   *
   * <p>There are best practices for |unit_name|:
   * <ul>
   * <li>do not abbreviate
   * <li>use the plural form
   * <li>use lowercase letters
   * <li>use one of the constants provided in {@link Metadata.Units} if possible
   * </ul>
   *
   * <p>For example, "milliseconds", "bytes", and "gigabytes" are all
   * well-formed.  "ms", "b", "gb", and "Bytes" are not.  Several constants are
   * provided for well-known values:
   * <ul>
   * <li>SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS
   * <li>BITS, BYTES
   * <li>KILOBYTES, MEGABYTES, GIGABYTES
   * <li>KIBIBYTES, MEBIBYTES, GIBIBYTES
   * </ul>
   */
  public Metadata setUnit(String unitName) {
    return annotate(UNIT, unitName);
  }

  Metadata addToDescription(String description) {
    appendAnnotation(DESCRIPTION, description);
    return this;
  }

  /**
   * If true, this metric will not be reported over the streamz rpc service,
   * but only visible through the /streamz servlet.
   */
  boolean isForEyesOnly() {
    return forEyesOnly;
  }

  Map<String, String> getAnnotations() {
    return annotations;
  }

  /**
   * Returns true if the metric is annotated as Cumulative.
   */
  public boolean isCumulative() {
    return TRUE_VAL.equals(annotations.get(CUMULATIVE));
  }

  /**
   * Returns true if the metric is annotated as Gauge.
   */
  public boolean isGauge() {
    return TRUE_VAL.equals(annotations.get(GAUGE));
  }
}