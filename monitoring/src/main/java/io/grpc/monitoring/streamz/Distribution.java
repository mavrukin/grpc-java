package io.grpc.monitoring.streamz;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.monitoring.streamz.proto.DistributionProto;
import java.util.Arrays;

/**
 * Distribution models a distribution of numeric sample data, and provides
 * various statistics of the data, such as its count, sum, mean, variance,
 * standard deviation, and a histogram.
 *
 * <p>The histogram bucketing function is customizable---it is a parametric
 * linear-plus-exponential function---but all such functions produce a
 * contiguous sequence of 2--100 buckets monotonically increasing from zero.
 * Values falling below this range or above this range are recorded in special buckets
 * UNDERFLOW and OVERFLOW respectively.
 *
 * <p>The primary use-cases for this class are (1) exporting aggregated
 * event data from the Streamz runtime, as in {@link EventMetric0} or {@code
 * Metric<Distribution>}.  In the Java code, it is an invariant that all Distributions
 * have a Bucketer throughout their lifetimes.
 *
 * <p>{@code Distribution} is <strong>not</strong> thread-safe. All access to
 * instances of {@code Distribution} should be from a single thread, or be
 * protected by an external lock. e.g. {@link EventMetricBase} and subclasses
 * have a lock for each cell in the metric which is acquired while recording
 * an event or reading the current value of the distribution.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public final class Distribution {
  private static final long[] NO_BUCKETS = {};

  private final Bucketer bucketer;
  private long count = 0L;
  private double mean = 0.0;
  private double sumOfSquaredDeviation = 0.0;

  // Histogram:
  private long overflow = 0L;
  private long underflow = 0L;
  private long[] buckets = NO_BUCKETS;  // length == bucketer.maxBuckets, but
                                        // the suffix of zeroes is usually
                                        // omitted.

  // Optional array of exemplars for each bucket. Note that exemplars for UNDERFLOW/OVERFLOW
  // buckets are ignored and will not be stored here.
  private DistributionProto.Exemplar[] exemplars = null;

  //// Constructors

  /**
   * Constructs an empty Distribution with the specified bucketing function.
   */
  public Distribution(Bucketer bucketer) {
    this.bucketer = Preconditions.checkNotNull(bucketer);
  }

  // Private copy constructor.  Use the public copy() method.
  private Distribution(Distribution that) {
    this.bucketer = that.bucketer;
    this.count = that.count;
    this.mean = that.mean;
    this.sumOfSquaredDeviation = that.sumOfSquaredDeviation;
    this.overflow = that.overflow;
    this.underflow = that.underflow;
    this.buckets = that.buckets.clone();

    if (that.exemplars != null) {
      this.exemplars = Arrays.copyOf(that.exemplars, that.exemplars.length);
    }
  }

  // Private from-proto constructor.  Use the public fromProto factory instead.
  private Distribution(DistributionProto input) throws InvalidProtocolBufferException {
    Preconditions.checkState(input.hasBucketer(), "Distribution must have a bucketer");
    this.bucketer = Bucketer.fromProto(input.getBucketer());

    this.count = input.getCount();
    this.mean = input.getMean();
    this.sumOfSquaredDeviation = input.getSumOfSquaredDeviation();
    this.overflow = input.getOverflow();
    this.underflow = input.getUnderflow();

    int numBuckets = 0;
    int bucketCount = input.getBucketCount();
    for (int i = 0; i < bucketCount; ++i) {
      long value = input.getBucket(i);
      numBuckets += value > 0 ? 1 : -value;
    }

    long total = overflow + underflow;
    this.buckets = new long[numBuckets];
    for (int i = 0, o = 0; i < bucketCount; ++i) { // oo is output cursor
      long value = input.getBucket(i);
      if (value > 0) {
        buckets[o++] = value;
        total += value;
      } else if (value < 0) {  // negative value => run-length of zeroes
        for (int j = 0; j < -value; ++j)
          buckets[o++] = 0;
      } else {  // value == 0
        // We explicitly forbid this case to avoid ambiguity.
        throw new InvalidProtocolBufferException("Encountered zero in DistributionProto.buckets");
      }
    }

    if (count != total) {
      throw new InvalidProtocolBufferException(
          "count (" + count + ") not equal to sum of buckets (" + total + ")");
    }

    if (input.getExemplarCount() > 0) {
      double lastValue = -java.lang.Double.MAX_VALUE;
      DistributionProto.Exemplar[] buffer = new DistributionProto.Exemplar[numBuckets];
      int length = 0;
      for (DistributionProto.Exemplar exemplar : input.getExemplarList()) {
        if (exemplar.getValue() < lastValue) {
          throw new InvalidProtocolBufferException("Exemplars are not in value-order");
        }
        lastValue = exemplar.getValue();
        int bucketId = bucketer.findBucket(exemplar.getValue());
        if (bucketId != Bucketer.OVERFLOW_BUCKET && bucketId != Bucketer.UNDERFLOW_BUCKET) {
          if (buffer[bucketId] != null) {
            throw new InvalidProtocolBufferException(
                "More than one Exemplar maps to the same bucket.");
          }
          if (bucketId >= numBuckets) {
            throw new InvalidProtocolBufferException(
                "Invalid exemplar bucket id " + bucketId + " - only " + numBuckets + " expected.");
          }
          buffer[bucketId] = exemplar;
          length = bucketId + 1; // bucket id can not decrease since exemplar list is ordered.
        }
      }
      if (length > 0) {
        exemplars = Arrays.copyOf(buffer, length);
      }
    }
  }

  /**
   * Returns a new Distribution which is a copy of this one.
   */
  public Distribution copy() {
    return new Distribution(this);
  }

  ///// Mutators

  /**
   * Adds {@code value} to the distribution.
   */
  public void add(double value) {
    addMultiple(value, 1);
  }

  /**
   * Adds {@code n} copies of {@code value} to the distribution.
   */
  public void addMultiple(double value, long n) {
    if (isValidSample(value, n)) {
      addMultipleToBucket(bucketer.findBucket(value), value, n);
    }
  }

  /**
   * Adds {@code value} to the distribution, with {@code exemplar}.
   */
  public void addWithExemplar(double value, DistributionProto.Exemplar exemplar) {
    Preconditions.checkNotNull(exemplar);
    Preconditions.checkArgument(value == exemplar.getValue(),
        "Exemplar value must equal passed value");
    if (isValidSample(value, 1)) {
      int bucketId = bucketer.findBucket(value);
      addMultipleToBucket(bucketId, value, 1);
      addExemplarToBucket(bucketId, exemplar);
    }
  }

  /**
   * Checks whether operation of adding {@code n} copies of the {@code value} to the distribution
   * will be processed or skipped (e.g. due to zero count or NaN or infinity value).
   *
   * @return true if provided value and count are valid, false - otherwise.
   */
  static boolean isValidSample(double value, long n) {
    Preconditions.checkArgument(n >= 0, "Number of copies must be non-negative, not %s", n);

    // Only positive count and finite values are considered to be valid.
    return n > 0 && !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /**
   * Adds {@code n} copies of {@code value} to the bucket {@code bucketId}.
   * <p>
   * Method assumes that all parameters are valid: {@code n} is greater than zero,
   * {@code value} is not NaN or infinity and that {@code bucketId} is calculated using
   * {@code getBucketer().findBucket(value)}. Validation is omitted for performance reasons.
   */
  void addMultipleToBucket(int bucketId, double value, long n) {
    count += n;

    // Method of provisional means: compute new mean and sum of squared deviation.
    double dev = value - mean;
    if (n == 1) {
      // The new_mean calculation directly divides (dev / new_count) instead of the
      // (dev * (1 / new_count) calculation to avoid unnecessary rounding errors.
      mean += dev / count;
      sumOfSquaredDeviation += dev * (value - mean);
    } else {
      mean += dev * (((double) n) / ((double) count));
      sumOfSquaredDeviation += dev * (value - mean) * n;
    }

    if (bucketId == Bucketer.UNDERFLOW_BUCKET) {
      underflow += n;
    } else if (bucketId == Bucketer.OVERFLOW_BUCKET) {
      overflow += n;
    } else {
      if (bucketId >= buckets.length) { // resize
        long[] oldBuckets = buckets;
        buckets = new long[bucketId + 1];
        System.arraycopy(oldBuckets, 0, buckets, 0, oldBuckets.length);
        // NOTE(adonovan): to save space we expand no more than absolutely
        // necessary, but this means quadratic expansion time in the worst case
        // (a monotonic series).  Is that bad?
      }
      buckets[bucketId] += n;
    }
  }

  /**
   * Adds {@code exemplar} instance to the bucket {@code bucketId} iff it is the first exemplar
   * instance for that bucket or it is newer than current instance.
   * <p>
   * Underflow and overflow buckets are ignored.
   * <p>
   * Method assumes that {@code exemplar} is valid and {@code bucketId} is calculated using
   * {@code getBucketer().findBucket(exemplar.getValue())}. Validation is omitted for performance
   * reasons.
   */
  void addExemplarToBucket(int bucketId, DistributionProto.Exemplar exemplar) {
    if (bucketId == Bucketer.UNDERFLOW_BUCKET || bucketId == Bucketer.OVERFLOW_BUCKET) {
      return;
    }
    if (exemplars == null) {
      exemplars = new DistributionProto.Exemplar[bucketId + 1];
    } else if (bucketId >= exemplars.length) { // resize
      DistributionProto.Exemplar[] oldExemplars = exemplars;
      exemplars = new DistributionProto.Exemplar[bucketId + 1];
      System.arraycopy(oldExemplars, 0, exemplars, 0, oldExemplars.length);
    }

    // Add the exemplar to the bucket if there isn't one there, or replace an existing one if
    // the timestamp on the new one is > the old one.  (For the purposes of this we treat no
    // timestamp as "infinite past".
    DistributionProto.Exemplar prev = exemplars[bucketId];
    if (prev == null
        || (prev.getTimestamp() == 0 && exemplar.getTimestamp() != 0)
        || (prev.getTimestamp() != 0 && exemplar.getTimestamp() != 0
            && prev.getTimestamp() < exemplar.getTimestamp())) {
      exemplars[bucketId] = exemplar;
    }
  }

  //// Accessors

  /** Returns the bucketing function used by this distribution. */
  public Bucketer getBucketer() { return bucketer; }

  /** Returns the number of samples in this distribution. */
  public long getCount() { return count; }

  /** Returns the mean of this distribution. */
  public double getMean() { return mean; }

  /** Returns the median of this distribution. */
  public double getMedian() {
    return getNthPercentile(50);
  }

  // Non-histogram statistics:

  /** Returns the sum-of-squared-deviation of this distribution. */
  public double getSumOfSquaredDeviation() { return sumOfSquaredDeviation; }

  /** Returns the sum of all the samples in this distribution. */
  public double getSum() { return count * mean; }

  /** Returns the standard deviation of this distribution. */
  public double getStandardDeviation() { return Math.sqrt(getVariance()); }

  /** Returns the (population) variance of this distribution. */
  public double getVariance() { return count == 0 ? 0.0 : sumOfSquaredDeviation / count; }

  // Histogram statistics:

  /** Returns the number of histogram buckets. */
  public int getBucketCount() { return bucketer.getMaxBuckets(); }

  /** Returns the smallest value that can be held in the i'th bucket. */
  public double getBucketMinimum(int bucketId) { return bucketer.getBucketMinimum(bucketId); }

  /**
   * Returns the exclusive upper bound of the i'th bucket.
   * @pre {@code bucketId} in interval {@code [0, getBucketCount())}.
   */
  public double getBucketMaximum(int bucketId) { return bucketer.getBucketMaximum(bucketId); }

  /**
   * Returns the number of samples in the i'th bucket.
   * @pre {@code bucketId} in interval
   * {@code [0, getBucketCount())}.  Never negative.
   */
  public long getBucketHeight(int bucketId) {
    return bucketId < buckets.length ? buckets[bucketId] : 0;
  }

  /**
   * Returns the exemplar for a particular bucket or null if there's no exemplar for the bucket.
   */
  public DistributionProto.Exemplar exemplarForBucket(int bucketId) {
    if (exemplars == null || bucketId >= exemplars.length
        || bucketId == Bucketer.UNDERFLOW_BUCKET || bucketId == Bucketer.OVERFLOW_BUCKET) {
      return null;
    }
    return exemplars[bucketId];
  }

  /**
   * Returns the number of samples in the histogram whose value
   * exceeded the range of the largest bucket.
   */
  public long getOverflowCount() { return overflow; }

  /**
   * Returns the number of samples in this histogram whose value
   * was smaller than the least bucket.
   */
  public long getUnderflowCount() { return underflow; }

  /** Encodes this Distribution as a protocol message, which is returned. */
  public DistributionProto toProto() {
    DistributionProto.Builder result = DistributionProto.newBuilder()
        .setCount(count)
        .setMean(mean)
        .setSumOfSquaredDeviation(sumOfSquaredDeviation)
        .setOverflow(overflow)
        .setUnderflow(underflow)
        .setBucketer(bucketer.toProto());

    // Zeroes are run-length encoded as negative values.
    long total = overflow + underflow;
    if (overflow > 0)  { result.setOverflow(overflow);   }
    if (underflow > 0) { result.setUnderflow(underflow); }
    int zeroes = 0;
    for (long i : buckets) {
      total += i;
      if (i == 0) {
        zeroes++;
      } else {
        if (zeroes > 0) {
          result.addBucket(-zeroes);
          zeroes = 0;
        }
        result.addBucket(i);
      }
    }
    Preconditions.checkState(count == total);  // sanity check, so we don't emit bad messages
    result.setBucketer(bucketer.toProto());
    if (exemplars != null) {
      // exemplars are ordered by buckets so they must be sorted by value already.
      for (DistributionProto.Exemplar exemplar : exemplars) {
        if (exemplar != null) {
          result.addExemplar(exemplar);
        }
      }
    }
    return result.build();
  }

  /**
   * Parses {@code input} and returns a Distribution.
   *
   * @throws IllegalArgumentException if the Bucketer parameters
   *   were invalid, either individually or together.
   * @throws InvalidProtocolBufferException if the protocol message was invalid
   *   in some other way.
   */
  public static Distribution fromProto(DistributionProto input)
      throws InvalidProtocolBufferException {
    return new Distribution(input);
  }

  @Override
  public String toString() {
    return String.format("Distribution(n=%d, mean=%.4g, stddev=%.4g)",
        count, mean, getStandardDeviation());
  }

  /**
   * Provide a detailed string representation of this object; useful for debugging.
   */
  public String toDebugString() {
    return MoreObjects.toStringHelper(this)
        .add("bucketer", bucketer)
        .add("count", count)
        .add("mean", mean)
        .add("stddev", getStandardDeviation())
        .add("overflow", overflow)
        .add("underflow", underflow)
        .add("buckets", Arrays.toString(buckets)).toString();
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException("not suitable as a key in a hash table");
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Distribution)) { return false; }
    Distribution that = (Distribution) o;
    // bucketers are canonicalized, so == is sufficient.
    return this.bucketer == that.bucketer &&
        this.count == that.count &&
        this.mean == that.mean &&
        this.sumOfSquaredDeviation == that.sumOfSquaredDeviation &&
        this.overflow == that.overflow &&
        this.underflow == that.underflow &&
        bucketsEqual(this.buckets, that.buckets);
  }

  // Returns true iff x and y are equal but for a suffix of zeroes.
  private static boolean bucketsEqual(long[] x, long[] y) {
    if (y.length < x.length) { return bucketsEqual(y, x); }
    for (int i = 0; i < x.length; ++i) {
      if (x[i] != y[i]) {
        return false;
      }
    }
    for (int i = x.length; i < y.length; ++i) {
      if (y[i] != 0L) {
        return false;
      }
    }
    return true;
  }

  private static double clamp(double value, double lowRange, double highRange) {
    if (value < lowRange) {
      return lowRange;
    } else if (value > highRange) {
      return highRange;
    } else {
      return value;
    }
  }

  /**
   * Returns the nth percentile.
   */
  public double getNthPercentile(double n) {
    if (count < 1) {
      return 0.0;  // special case.
    }

    n = clamp(n, 0, 100);

    // The number of samples whose value is less than or equal to the
    // result.  We permit fractional samples, since it makes a
    // difference in the fractional bucket case.
    double numSamples = count * n / 100.0;

    double seen = underflow;
    if (seen >= numSamples) {
      return 0.0;
    }
    for (int i = 0; i < buckets.length; ++i) {
      assert(seen < numSamples);

      double height = getBucketHeight(i);
      if (seen + height >= numSamples) {  // consume some or all of this bucket
        // We assume that a bucket of width w containing n samples has
        // them equally spaced at intervals of w/n above the
        // bucket_minimum.

        // Division by zero is impossible due to the loop invariant.
        double fraction = (numSamples - seen) / height;

        double left  = getBucketMinimum(i);  // (inclusive)
        double right = getBucketMaximum(i);  // (exclusive)

        return left + fraction * (right - left);
      }
      seen += height;
    }
    return getBucketMaximum(getBucketCount() - 1);
  }

  /**
   * Returns an estimate of the fraction of the samples that are strictly
   * less than x. If x falls in the underflow or overflow bucket, the return
   * value will be 0.0 or 1.0, respectively. For all other buckets, linear
   * interpolation is used if x does not fall exactly on a bucket boundary.
   */
  public double getFractionLessThan(double x) {
    if (count == 0) {
      return 0;
    }

    int bn = bucketer.findBucket(x);
    if (bn == Bucketer.UNDERFLOW_BUCKET) {
      return 0;
    }
    if (bn == Bucketer.OVERFLOW_BUCKET) {
      if (getBucketMaximum(getBucketCount() - 1) == x) {
        // If they pass in the exclusive upper bound of the last final bucket, we can give
        // them an accurate number below by treating it as a value in that bucket.
        bn = getBucketCount() - 1;
      } else {
        return 1;
      }
    }

    // Count the contents of the buckets below the one x falls into.
    double countLessThanX = underflow;
    int limit = Math.min(bn, buckets.length);
    for (int i = 0; i < limit; ++i) {
      countLessThanX += buckets[i];
    }

    // Linearly interpolate for the count to add from the bucket in which x falls.
    if (bn < buckets.length) {
      // As in getNthPercentile, we use linear interpolation.
      double fraction = (x - getBucketMinimum(bn)) / (getBucketMaximum(bn) - getBucketMinimum(bn));
      countLessThanX += fraction * getBucketHeight(bn);
    }

    return countLessThanX / count;
  }
}
