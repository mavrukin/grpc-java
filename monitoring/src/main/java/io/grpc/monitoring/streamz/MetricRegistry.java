package io.grpc.monitoring.streamz;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.concurrent.ThreadSafe;
import java.util.logging.Logger;

/**
 * An internal registry of metrics. Used to track registration of metrics and
 * optionally supports duplicate metric names.
 *
 * This class is <b>thread safe</b>.
 *
 * @author jdb@google.com (Jon Blumenthal)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@ThreadSafe
final class MetricRegistry {
  /**
   * Class used to keep track of trigger execution during applyToMetric???() calls,
   * ensuring that each trigger is executed exactly once.
   *
   * @author nsakharo@google.com (Nick Sakharov)
   */
  static final class TriggerEnvironment {
    private final Set<CallbackScope> activeScopes = Sets.newIdentityHashSet();

    TriggerEnvironment() {}

    /**
     * Executes triggers associated with the metric if the metric supports triggers
     * and if they were not executed during the lifetime of this object.
     */
    void fireTriggerIfNeeded(GenericMetric<?, ?> metric) {
      if (metric instanceof CallbackMetric<?, ?>) {
        CallbackScope scope = ((CallbackMetric<?, ?>) metric).getScope();
        if (scope != null && activeScopes.contains(scope)) {
          scope.lockAndRunTriggers();
          // TODO(nsakharo): Perhaps we should monitor lockAndRunTriggers() calls and log error
          // whenever TriggerEnvironment has not been destroyed (freeing locks in the process) for
          // a long time (e.g. 1 minute).
        }
      }
    }

    /**
     * Releases any locks set during trigger execution. Must be invoked after
     * processing all requested metrics to ensure that metric values will not
     * mutate after trigger execution but before processing (read - all callback
     * invocations done by applyToMetric() method) has been completed.
     */
    void destroy() {
      for (CallbackScope scope : activeScopes) {
        scope.unlock();
      }
    }
  }

  private static final Logger logger = Logger.getLogger(MetricRegistry.class.getName());

  private final AtomicBoolean failOnDuplicateMetricNames;
  private final ConcurrentNavigableMap<String, GenericMetric<Object, ?>> metrics;

  /**
   * Creates a new MetricRegistry that fails on duplicate metric names.
   */
  MetricRegistry() {
    this.failOnDuplicateMetricNames = new AtomicBoolean(true);
    this.metrics = new ConcurrentSkipListMap<String, GenericMetric<Object, ?>>();
  }

  /**
   * Adds a metric to this registry.
   *
   * @throws IllegalStateException If a metric already in the registry has the
   *         same name, and {@link #failOnDuplicateMetricNames} is
   *         {@code true}.
   */
  void add(GenericMetric<?, ?> metric) {
    if (metrics.putIfAbsent(metric.getName(), metric.castForReading()) != null) {
      Preconditions.checkState(!failOnDuplicateMetricNames.get(),
          "Duplicate metric of same name: %s with annotations %s.", metric, metric.getAnnotations());

      if (logger.isLoggable(Level.WARNING)) {
        logger.warning("Observed registration of multiple metrics with the same name: "
            + metric + ". Ignoring subsequent registration. Set a system property -D"
            + "streamz.sanityChecks=\"strict\" if you want this check to fail with an exception.");
      }
    }
  }

  private void applyToMetric(GenericMetric<Object, ?> metric,
      Receiver<GenericMetric<Object, ?>> callback, TriggerEnvironment env) {
    env.fireTriggerIfNeeded(metric);
    callback.accept(metric);
  }

  /**
   * Applies the specified Receiver to the named metric, while holding the
   * intrinsic lock. If the metric doesn't exist,
   * {@link Receiver#accept(Object) is never called}. {@code callback} should
   * not retain a reference to its argument once it completes.
   */
  void applyToMetric(String name, Receiver<GenericMetric<Object, ?>> callback) {
    GenericMetric<Object, ?> metric = metrics.get(name);
    if (metric != null) {
      TriggerEnvironment env = new TriggerEnvironment();
      try {
        applyToMetric(metric, callback, env);
      } finally {
        env.destroy();
      }
    }
  }

  /**
   * Applies the specified Receiver, while holding the intrinsic lock, to all
   * metrics whose names are "beneath" {@code directory}, according to the usual
   * UNIX convention. {@code directory} must have a trailing "/", or be empty,
   * in which case it is considered to match all metrics.
   * <p>
   * The metrics are guaranteed to be passed to the {@code callback} in ascending lexicographical
   * order by their names.
   *
   * <p>{@code callback} should not retain a reference to its argument once it completes.
   */
  void applyToMetricsBeneath(String directory,
      Receiver<GenericMetric<Object, ?>> callback) {
    validateDirectory(directory);

    TriggerEnvironment env = new TriggerEnvironment();
    try {
      for (GenericMetric<Object, ?> metric : getMetricsBeneath(directory)) {
        applyToMetric(metric, callback, env);
      }
    } finally {
      env.destroy();
    }
  }

  /**
   * Applies the specified Receiver, while holding the intrinsic lock, to all
   * metrics matching provided list of names. Names ending with trailing "/" (slash),
   * per usual UNIX conventions, will be considered to be {@code directory} names and
   * will rely on prefix matching instead. An empty string is considered to match all metrics.
   *
   * <p>{@code callback} should not retain a reference to its argument once it
   * completes.
   */
  void applyToMetrics(Iterable<String> names, Receiver<GenericMetric<Object, ?>> callback) {

    // Ensure that metrics are applied only once.
    // We are relying on the fact that metrics are never deleted once registered, so we don't have
    // to worry about some metrics being removed while callback is being applied.
    Set<GenericMetric<Object, ?>> metricSet = Sets.newHashSet();
    for (String name : names) {
      if (name.isEmpty()) {
        metricSet.addAll(metrics.values());
        break;  // All metrics have been added -- no need to process subsequent names.
      } else if (name.endsWith("/")) {
        metricSet.addAll(getMetricsBeneath(name));
      } else {
        GenericMetric<Object, ?> metric = metrics.get(name);
        if (metric != null) {
          metricSet.add(metric);
        }
      }
    }
    TriggerEnvironment env = new TriggerEnvironment();
    try {
      for (GenericMetric<Object, ?> metric : metricSet) {
        applyToMetric(metric, callback, env);
      }
    } finally {
      env.destroy();
    }
  }

  /**
   * For the purpose of reading only metric metadata, applies the specified Receiver to
   * all metrics while holding the namespace lock. No triggers are invoked before applying
   * the Receiver, and {@link GenericMetric#applyToEachCell} must not be called within
   * {@code callback}.
   * <p>
   * The metrics are guaranteed to be passed to the {@code callback} in ascending lexicographical
   * order by their names.
   *
   * <p>
   * {@code callback} should not retain a reference to its argument once it completes.
   */
  void applyToMetricMetadata(Receiver<GenericMetric<Object, ?>> callback) {
    for (GenericMetric<Object, ?> metric : metrics.values()) {
      callback.accept(metric);
    }
  }

  /**
   * For the purpose of reading only metric metadata, applies the specified {@link Receiver} to
   * all metrics whose names are "beneath" {@code directory} according to the usual UNIX convention,
   * while holding the namespace lock.
   * <p>
   * No triggers are invoked before applying the receiver and {@link GenericMetric#applyToEachCell}
   * must not be called within {@code callback}. {@code callback} should not retain a
   * reference to its argument once it completes.
   * <p>
   * The metrics are guaranteed to be passed to the {@code callback} in ascending lexicographical
   * order by their names.
   *
   * @param directory directory that contains the metrics to be processed by the {@code callback}.
   *     {@code directory} must have a trailing "/" or be empty.
   * @param callback the receiver that will process the matching metrics.
   */
  void applyToMetricMetadataBeneath(String directory, Receiver<GenericMetric<Object, ?>> callback) {
    validateDirectory(directory);

    for (GenericMetric<Object, ?> metric : getMetricsBeneath(directory)) {
      callback.accept(metric);
    }
  }

  /**
   * Returns a live sorted {@link Collection} view of the metrics that reside under the given
   * directory (i.e. whose names starts with {@code directory}).
   */
  private Collection<GenericMetric<Object, ?>> getMetricsBeneath(final String directory) {
    SortedMap<String, GenericMetric<Object, ?>> tailMetrics = metrics.tailMap(directory);
    Optional<String> nextNonMatchingMetricName = Iterables.tryFind(tailMetrics.keySet(),
        new Predicate<String>() {
          @Override public boolean apply(String input) {
            return !input.startsWith(directory);
          }
        });
    if (nextNonMatchingMetricName.isPresent()) {
      return tailMetrics.headMap(nextNonMatchingMetricName.get()).values();
    }
    return tailMetrics.values();
  }

  private static void validateDirectory(String directory) {
    Preconditions.checkArgument(directory.endsWith("/") || directory.isEmpty(),
        "The directory must end with a slash or be empty: %s",
        directory);
  }

  /**
   * Gets all metrics in this registry. This is an immutable snapshot and the
   * resulting ImmutableSet will not reflect changes to the underlying registry.
   */
  protected ImmutableSet<GenericMetric<Object, ?>> getMetrics() {
    return ImmutableSet.copyOf(metrics.values());
  }

  /**
   * Returns whether a metric exists in this registry.
   */
  protected boolean metricExists(String metricName) {
    return metrics.containsKey(metricName);
  }

  /**
   * Sets if duplicate metrics are allowed on this registry.
   */
  protected void setFailOnDuplicateMetricNames(
      boolean failOnDuplicateMetricNames) {
    this.failOnDuplicateMetricNames.set(failOnDuplicateMetricNames);
  }

}