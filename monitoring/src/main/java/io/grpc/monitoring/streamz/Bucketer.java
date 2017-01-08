package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.monitoring.streamz.proto.BucketerProto;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucketing function for histograms recorded by class {@link Distribution}.
 *
 * <p>See "Histogram" section in distribution.proto for detailed explanation of
 * the bucketing function.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@Immutable
public final class Bucketer {

  /**
   * Denotes the topmost histogram bucket, whose upper bound is +infinity.
   */
  public static final int OVERFLOW_BUCKET = -1;

  /**
   * Denotes the least histogram bucket, whose lower bound is -infinity.
   */
  public static final int UNDERFLOW_BUCKET = -2;

  /**
   * The number buckets in any Bucketer will be clipped to (@code MAX_NUM_FINITE_BUCKETS).
   */
  public static final int MAX_NUM_FINITE_BUCKETS = 5000;

  // Maps Bucketers to their canonical instance.  (Effectively a set.)
  private static final ConcurrentHashMap<Bucketer, Bucketer> BUCKETER_CACHE =
      new ConcurrentHashMap<Bucketer, Bucketer>();

  private static final long UINT_MAX = (1L << 32) - 1;

  /**
   * Default value: 17 buckets covering the powers of four (not 16, because
   * zero has its own bucket).  Intentionally coarse to save memory throughout
   * the system.
   */
  public static final Bucketer DEFAULT = powersOf(4);

  /**
   * Minimum bucketer: only a single bucket, enough to compute the
   * arithmetic mean, but no histograms.
   * TODO(gritter): This still has needless underflow and overflow
   * buckets. Minimize it further, http://b/8732667
   *
   * Note that for the time being, the bucket maximum needs to be
   * finite in order to prevent Monarch to treat this bucket as
   * the overflow bucket.
   */
  public static final Bucketer NONE = fixedWidth(Double.MAX_VALUE, 1);

  /**
   * Returns the canonical Bucketer for the specified coefficients.
   *
   * <p>Thread-safe.
   *
   * <p>See distribution.proto for details for the function.
   */
  private static Bucketer createInternal(double width, double growthFactor,  double scaleFactor,
      int maxBuckets) {
    Bucketer bucketer = new Bucketer(width, growthFactor, scaleFactor, maxBuckets);
    Bucketer canonical = BUCKETER_CACHE.putIfAbsent(bucketer, bucketer);
    return canonical == null ? bucketer : canonical;
    // TODO(adonovan): optimise cache hit case by not fully-constructing a
    // Bucketer (with boundaries) only to use it as a key and discard it.
    // Define a Coefficients key struct instead.
  }

  /**
   * Parses {@code input} and returns a Bucketer.
   *
   * @throws InvalidProtocolBufferException if the protocol message is invalid.
   */
  public static Bucketer fromProto(BucketerProto input) throws InvalidProtocolBufferException {
    // parameterized bucketer
    if (input.getLowerBoundsCount() == 0) {
      return createInternal(input.getWidth(), input.getGrowthFactor(), input.getScaleFactor(),
          input.getNumFiniteBuckets());
    } else {
      double[] bounds = new double[input.getLowerBoundsCount()];
      for (int i = 0; i < input.getLowerBoundsCount(); ++i) {
        bounds[i] = input.getLowerBounds(i);
      }
      return custom(bounds);
    }
  }

  /**
   * Serialization to protocol message.
   */
  public BucketerProto toProto() {
    BucketerProto.Builder result = BucketerProto.newBuilder();
    if (useCustomBoundaries) {
      result.setNumFiniteBuckets(0);  // this is a required field, set it to default value.
      for (double i : boundaries) {
        result.addLowerBounds(i);
      }
    } else {
      if (this.width != 0.0) {
        result.setWidth(this.width);
      }
      if (this.growthFactor != 0.0) {
        result.setGrowthFactor(this.growthFactor);
      }
      if (this.scaleFactor != 1.0) {
        result.setScaleFactor(this.scaleFactor);
      } else {
        // We need to set the default value when its non-zero for proto3 compatability
        result.setScaleFactor(1.0d);
      }
      result.setNumFiniteBuckets(this.maxBuckets);
    }

    return result.build();
  }

  /**
   * Returns the canonical Bucketer for specified custom bounds.
   *
   * Thread-safe.
   */
  public static Bucketer custom(double[] bounds) {
    Bucketer bucketer = new Bucketer(bounds);
    Bucketer canonical = BUCKETER_CACHE.putIfAbsent(bucketer, bucketer);
    return canonical == null ? bucketer : canonical;
  }

  /**
   * Factory for bucketing functions that put each power of {@code base} in a separate bucket.
   * {@code base} must be greater than 1.01.
   * <p>
   * The lower bound of each bucket is calculated as the following:
   * {@code lowerbound = 1.0 * Math.pow(growthFactor, i - 1)},
   * where {@code i} is the number of a bucket (1, 2, ...).
   * <p>
   * Thread-safe.
   */
  public static Bucketer powersOf(double growthFactor) {
    return scaledPowersOf(growthFactor, 1.0, UINT_MAX);
  }

  /**
   * Factory for bucketing functions that put each power of {@code base} in a separate bucket.
   * {@code base} must be positive.
   * <p>
   * The lower bound of each bucket is calculated as the following:
   * {@code lowerbound = scaleFactor * Math.pow(growthFactor, i - 1)}, where {@code i} is the
   * number of a bucket (1, 2, ...).
   * <p>
   * Thread-safe.
   */
  public static Bucketer scaledPowersOf(double growthFactor, double scaleFactor, double maxValue) {
    Preconditions
        .checkArgument(growthFactor >= 1.01, "growthFactor must be >= 1.01, is %s", growthFactor);
    Preconditions.checkArgument(scaleFactor > 0.0, "scaleFactor must be > 0.0, is %s", scaleFactor);
    Preconditions.checkArgument(maxValue >= 0.0, "maxValue must be >= 0.0, is %s", maxValue);
    int numFiniteBuckets = 1
        + (int) Math.ceil((Math.log(maxValue) - Math.log(scaleFactor)) / Math.log(growthFactor));
    if (numFiniteBuckets < 1) {
      numFiniteBuckets = 1; // When scaleFactor > maxValue
    }
    return createInternal(0.0, growthFactor, scaleFactor, numFiniteBuckets);
  }

  /**
   * Factory for creating a bucketing functions of {@code maxBuckets} equal buckets of width
   * {@code width}, which must be greater than zero.  {@code maxBuckets} must be at least 2.
   *
   * Thread-safe.
   */
  public static Bucketer fixedWidth(double width, int maxBuckets) {
    return createInternal(width, 0.0, 1.0, maxBuckets);
  }

  //// Instance members:

  private final double width;
  private final double growthFactor;
  private final double scaleFactor;
  private final int maxBuckets;
  // When true, ignore width, growthFactor, scaleFactor, maxBuckets.
  private final boolean useCustomBoundaries;

  // Inclusive lower bounds of each bucket.  Contains n+1 buckets; the dummy
  // last element is present only to demarcate the top of the last real bucket.
  private final double[] boundaries;

  private Bucketer(double width, double growthFactor, double scaleFactor, int maxBuckets) {
    // check parameters
    Preconditions
        .checkArgument(maxBuckets >= 1, "Histograms must have at least 1 bucket: %s", maxBuckets);
    Preconditions.checkArgument((growthFactor == 0.0 || growthFactor > 1) || (width != 0.0),
        "Growth factors, if given, must be greather than 1.0: %s", growthFactor);
    Preconditions
        .checkArgument(width >= 0.0, "Negative width coefficients are not allowed: %s", width);
    Preconditions.checkArgument(growthFactor != 0.0 || width != 0.0,
        "Must give a growth factor or a width.");
    Preconditions
        .checkArgument(!Double.isInfinite(width), "Bucket width must be finite: %s", width);
    Preconditions
        .checkArgument(scaleFactor > 0.0, "Scale factors of 0.0 or less are not allowed: %s",
            scaleFactor);
    Preconditions.checkArgument(width == 0.0 || scaleFactor == 1.0,
        "Cannot give scale factor with fixed width");

    // clip maxBuckets;
    if (maxBuckets > MAX_NUM_FINITE_BUCKETS) {
      maxBuckets = MAX_NUM_FINITE_BUCKETS;
    }

    this.width = width;
    this.growthFactor = growthFactor;
    this.scaleFactor = scaleFactor;
    this.maxBuckets = maxBuckets;
    this.useCustomBoundaries = false;

    this.boundaries = new double[maxBuckets + 1];
    boundaries[0] = 0.0;
    for (int i = 1; i < maxBuckets + 1; ++i) {
      double lowerbound = width * i;
      if (growthFactor > 0.0) {
        lowerbound += scaleFactor * Math.pow(growthFactor, i - 1);
      }
      boundaries[i] = lowerbound;
      Preconditions
          .checkArgument(boundaries[i - 1] < lowerbound, "Bucketing function not monotonic: %s",
              this);
    }
  }

  // Create Bucketer with custom buckets boundaries.
  private Bucketer(double[] customBounds) {
    Preconditions.checkArgument(customBounds.length >= 2,
        "Custom bounds must have at least 2 buckets, is %s", customBounds.length);
    for (int i = 1; i < customBounds.length; ++i) {
      Preconditions.checkArgument(customBounds[i] >= customBounds[i - 1],
          "Custom bounds must be monotonically increasing");
    }

    this.width = 0.0;
    this.growthFactor = 0.0;
    this.scaleFactor = 1.0;
    this.maxBuckets = customBounds.length - 1;  // index of the last bucket

    this.useCustomBoundaries = true;
    this.boundaries = new double[customBounds.length];
    System.arraycopy(customBounds, 0, this.boundaries, 0, customBounds.length);
  }

  //// Parameters

  /**
   * Returns the {@code width} parameter, which controls linear component.
   */
  public double getWidthCoefficient() {
    return width;
  }

  /**
   * Returns the {@code growth_factor} parameter, which controls the exponential component.
   */
  public double getGrowthFactor() {
    return growthFactor;
  }

  /**
   * Returns the {@code scale_factor} parameter, which controls the exponential component.
   */
  public double getScaleFactor() {
    return scaleFactor;
  }

  /**
   * Returns the maximum number of buckets.  Always greater than 1.
   */
  public int getMaxBuckets() {
    return maxBuckets;
  }

  //// Bucketer functions:

  /**
   * Returns the smallest value that can be held in the i'th bucket.
   */
  public double getBucketMinimum(int bucketId) {
    return boundaries[bucketId];
  }

  /**
   * Returns the exclusive upper bound of the i'th bucket.
   */
  public double getBucketMaximum(int bucketId) {
    return boundaries[bucketId + 1];
  }

  /**
   * Returns the appropriate histogram bucket for {@code value}.
   *
   * If {@code value} is in range, returns the bucket index, which is in the interval
   * [0, max_buckets()). If {@code value} is smaller than the smallest bucket, returns
   * {@link #UNDERFLOW_BUCKET}. If {@code value} is larger than the largest bucket, returns
   * {@link #OVERFLOW_BUCKET}.
   */
  public int findBucket(double value) {
    if (value < boundaries[0]) {
      return UNDERFLOW_BUCKET;
    } else if (value >= boundaries[maxBuckets]) {
      return OVERFLOW_BUCKET;
    } else {
      int x = Arrays.binarySearch(boundaries, value);
      int bucketId = x < 0
          ? -2 - x  // x = -1-p where p = index of first element greater than {value}
          : x;
      Preconditions.checkState(bucketId < maxBuckets);  // already handled by OVERFLOW.
      return bucketId;
    }
  }

  @Override
  public String toString() {
    if (useCustomBoundaries) {
      StringBuilder sb = new StringBuilder("Bucketer (useCustomBoundaries, boundaries={");
      for(int i = 0; i < boundaries.length; ++i) {
        sb.append(boundaries[i]);
        if (i != boundaries.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("})");
      return sb.toString();
    } else {
      return String.format("Bucketer(width=%s, growthFactor=%s, scaleFactor=%s, maxBuckets=%s)",
          width, growthFactor, scaleFactor, maxBuckets);
    }
  }

  @Override
  public int hashCode() {
    return useCustomBoundaries ? Arrays.hashCode(boundaries) :
        (int)(maxBuckets + width + scaleFactor + growthFactor);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof Bucketer)) {
      return false;
    }
    Bucketer that = (Bucketer)o;
    if (this.useCustomBoundaries != that.useCustomBoundaries) {
      return false;
    } else if (this.useCustomBoundaries) {
      return Arrays.equals(this.boundaries, that.boundaries);
    } else {
      return this.width == that.width && this.growthFactor == that.growthFactor &&
          this.scaleFactor == that.scaleFactor && this.maxBuckets == that.maxBuckets;
    }
  }
}