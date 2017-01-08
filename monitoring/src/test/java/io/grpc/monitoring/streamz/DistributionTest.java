package io.grpc.monitoring.streamz;


import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.monitoring.streamz.proto.BucketerProto;
import io.grpc.monitoring.streamz.proto.DistributionProto;
import java.util.Random;
import junit.framework.TestCase;

public class DistributionTest extends TestCase {
  public void testBasics() throws Exception {
    Distribution d = new Distribution(Bucketer.DEFAULT);
    assertEquals(Bucketer.DEFAULT.getMaxBuckets(),     d.getBucketCount());
    assertEquals(Bucketer.DEFAULT.getBucketMinimum(4), d.getBucketMinimum(4));
    assertEquals(Bucketer.DEFAULT.getBucketMaximum(4), d.getBucketMaximum(4));

    // empty
    assertEquals(0,      d.getCount());
    assertEquals(0.0,    d.getMean());
    assertEquals(0.0,    d.getSum());
    assertEquals(0.0,    d.getStandardDeviation());
    assertEquals(0.0,    d.getVariance());
    assertEquals(0L,     d.getBucketHeight(1));
    assertEquals(0L,     d.getUnderflowCount());
    assertEquals(0L,     d.getOverflowCount());

    // {1}
    d.add(1.0);
    assertEquals(1,      d.getCount());
    assertEquals(1.0,    d.getMean());
    assertEquals(1.0,    d.getSum());
    assertEquals(0.0,    d.getStandardDeviation());
    assertEquals(0.0,    d.getVariance());
    assertEquals(1L,     d.getBucketHeight(1));

    // {1,2,3,4,5,6,7,8.9,10}
    for (int ii = 2; ii <= 10; ++ii) { d.add(ii); }
    assertEquals(10,     d.getCount());
    assertEquals(5.5,    d.getMean());
    assertEquals(55.0,   d.getSum());
    assertEquals(2.8723, d.getStandardDeviation(), 0.0001);
    assertEquals(8.25,   d.getVariance(), 0.01);
    assertEquals(0L,     d.getBucketHeight(0)); // [0-1)
    assertEquals(3L,     d.getBucketHeight(1)); // [1-4)
    assertEquals(7L,     d.getBucketHeight(2)); // [4-16)

    d.add(-42);  // underflow
    assertEquals(11,     d.getCount());
    assertEquals(1.182,  d.getMean(), 0.001);
    assertEquals(13.0,   d.getSum(), 0.001);
    assertEquals(1L,     d.getUnderflowCount());
    assertEquals(0L,     d.getOverflowCount());

    d.add(1E42);  // overflow
    assertEquals(12,     d.getCount());
    assertEquals(8.3E40, d.getMean(), 1E39);
    assertEquals(1E42,   d.getSum(), 1E40);
    assertEquals(1L,     d.getUnderflowCount());
    assertEquals(1L,     d.getOverflowCount());

    d = new Distribution(Bucketer.DEFAULT);
    d.add(Double.NaN);  // NaN and Infinities should be ignored.
    assertEquals(0,      d.getCount());
    assertEquals(0.0,    d.getSum());
    assertEquals(0L,     d.getUnderflowCount());
    assertEquals(0L,     d.getOverflowCount());

    d.add(Double.POSITIVE_INFINITY);
    assertEquals(0,      d.getCount());
    assertEquals(0.0,    d.getSum());
    assertEquals(0L,     d.getUnderflowCount());
    assertEquals(0L,     d.getOverflowCount());
  }

  // Tests that addMultiple() behaves exactly the same as calling AddSample() multiple times.
  // Ported from google3/monitoring/streamz/internal/distribution_test.cc:AddMultipleSamples.
  public void testMultiple() throws Exception {
    Distribution d1 = new Distribution(Bucketer.DEFAULT);
    Distribution d2 = new Distribution(Bucketer.DEFAULT);

    // {1, 1, 1}
    d1.addMultiple(100.0, 0);
    assertEquals(0,      d1.getCount());
    assertEquals(0.0,    d1.getMean());
    assertEquals(0.0,    d1.getSumOfSquaredDeviation());

    Random random = new Random(0);
    final double maxVal = d1.getBucketMinimum(d1.getBucketCount());
    for (int i = 0; i < 100; ++i) {
      int copies = random.nextInt(1000);
      double val = random.nextDouble() * maxVal;

      d1.addMultiple(val, copies);
      for (int round = 0; round < copies; ++round) {
        d2.add(val);
      }
    }

    assertEquals(d1.getCount(), d2.getCount());
    // Check that the relative errors of double typed stats are negligible.
    assertEquals(0.0, (d1.getMedian() - d2.getMedian()) / d1.getMedian(), 1E-9);
    assertEquals(0.0, (d1.getMean() - d2.getMean()) / d1.getMean(), 1E-9);
    assertEquals(0.0, (d1.getSum() - d2.getSum()) / d1.getSum(), 1E-9);
    assertEquals(0.0, (d1.getSumOfSquaredDeviation() - d2.getSumOfSquaredDeviation())
        / d1.getSumOfSquaredDeviation(), 1E-9);

    // Check that two distributions have same histograms.
    assertEquals(d1.getBucketCount(), d2.getBucketCount());
    for (int i = 0; i <= d1.getBucketCount(); ++i) {
      assertEquals(d1.getBucketHeight(i), d2.getBucketHeight(i));
    }
  }

  public void testCopyAndEquals() throws Exception {
    // Empty distributions not equal unless they have the same bucketer.
    assertFalse(new Distribution(Bucketer.powersOf(10)).equals(
        new Distribution(Bucketer.powersOf(4))));

    Distribution d1 = new Distribution(Bucketer.DEFAULT);
    d1.add(42);

    Distribution d2 = d1.copy();
    assertEquals(d1, d2);
    assertEquals(1,    d2.getCount());
    assertEquals(42.0, d2.getSum());

    d1.add(59);
    assertFalse(d1.equals(d2));
    assertEquals(2,     d1.getCount());
    assertEquals(101.0, d1.getSum());

    d2.add(59);
    assertEquals(d1, d2);
    assertEquals(2,     d2.getCount());
    assertEquals(101.0, d2.getSum());
  }

  public void testProtoEncodingAndDecoding() throws Exception {
    Distribution d1 = new Distribution(Bucketer.DEFAULT);
    for (int ii = 0; ii < 1000; ++ii) { d1.add(ii); }
    DistributionProto.Exemplar exemplar = DistributionProto.Exemplar.newBuilder()
        .setValue(427)
        .build();
    d1.addWithExemplar(427, exemplar);

    DistributionProto.Exemplar exemplar2 = DistributionProto.Exemplar.newBuilder()
        .setValue(2)
        .build();
    d1.addWithExemplar(2, exemplar2);

    DistributionProto.Exemplar exemplar3 = DistributionProto.Exemplar.newBuilder()
        .setValue(4)
        .build();
    d1.addWithExemplar(4, exemplar3);

    assertEquals(exemplar, d1.exemplarForBucket(5));
    assertEquals(exemplar2, d1.exemplarForBucket(1));
    assertEquals(exemplar3, d1.exemplarForBucket(2));

    DistributionProto p = d1.toProto();

    assertEquals(3, p.getExemplarCount());
    assertEquals(2.0, p.getExemplar(0).getValue());
    assertEquals(4.0, p.getExemplar(1).getValue());
    assertEquals(427.0, p.getExemplar(2).getValue());

    Distribution d2 = Distribution.fromProto(p);
    assertEquals(d1, d2);
    assertEquals(exemplar, d2.exemplarForBucket(5));
    assertEquals(exemplar2, d2.exemplarForBucket(1));
    assertEquals(exemplar3, d2.exemplarForBucket(2));

    // Make sure this works with custom bucketers -- that requires using the BucketerProto field.
    double[] bounds = {1.0, 2.0, 3.0, 10000.0};
    Distribution d3 = new Distribution(Bucketer.custom(bounds));
    d1.add(3);
    d1.add(55);
    DistributionProto p2 = d3.toProto();
    Distribution d4 = Distribution.fromProto(p2);
    assertEquals(d3, d4);


    // Introduce an inconsistency between buckets and count:
    try {
      Distribution.fromProto(p.toBuilder().setUnderflow(1).build());
      fail();
    } catch (InvalidProtocolBufferException e) {
      assertEquals("count (1003) not equal to sum of buckets (1004)", e.getMessage());
    }

    // Make the bucketer coefficients bad:
    try {
      DistributionProto.Builder b = p.toBuilder();
      b.setBucketer(p.getBucketer().toBuilder().setGrowthFactor(-1.0).build());
      Distribution.fromProto(b.build());
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Growth factors, if given, must be greater than 1.0: -1.0", e.getMessage());
    }
    try {
      DistributionProto.Builder b = p.toBuilder();
      b.setBucketer(p.getBucketer().toBuilder().setGrowthFactor(1.0).build());
      Distribution.fromProto(b.build());
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Growth factors, if given, must be greater than 1.0: 1.0", e.getMessage());
    }

    // Test a zero-value (illegal) in the wire-encoding.
    try {
      Distribution.fromProto(p.toBuilder().setBucket(3, 0).build());
      fail();
    } catch (InvalidProtocolBufferException e) {
      assertEquals("Encountered zero in DistributionProto.buckets", e.getMessage());
    }
  }

  public void testProtoRunLengthEncodingAndDecoding() throws Exception {
    Distribution d1 = new Distribution(Bucketer.DEFAULT);
    for (int ii = 500; ii < 1000; ++ii) { d1.add(ii); }

    DistributionProto p = d1.toProto();

    // Buckets 0-4 should all have been 0 and been run-length-encoded
    assertEquals(-5, p.getBucket(0));

    Distribution d2 = Distribution.fromProto(p);
    assertEquals(d1, d2);
    assertEquals(0, d2.getBucketHeight(0));  // 0-1
    assertEquals(0, d2.getBucketHeight(1));  // 1-4
    assertEquals(0, d2.getBucketHeight(2));  // 4-16
    assertEquals(0, d2.getBucketHeight(3));  // 16-64
    assertEquals(0, d2.getBucketHeight(4));  // 64-256
    assertFalse(0 == d2.getBucketHeight(5)); // 256-512
  }

  private double square(double x) { return x * x; }

  public void testMethodOfProvisionalMeans() throws Exception {
    // Copied from C++ test suite.
    // The Method of Provisional Means produces the correct variance even with
    // high absolute values, but notice how the naive sum-of-squares method loses
    // precision: the final assertion fails as K exceeds 2^25.
    for (int ii = 0; ii < 32; ++ii) {
      final long K = 1L << ii;
      Distribution d = new Distribution(Bucketer.DEFAULT);

      d.add(K + 1);
      assertEquals(K + 1.0, d.getMean());
      assertEquals(0.0, d.getVariance());

      d.add(K + 2);
      assertEquals(K + 1.5, d.getMean());
      assertEquals(0.25, d.getVariance());

      d.add(K + 3);
      assertEquals(K + 2.0, d.getMean());
      assertEquals(2/3.0, d.getVariance());

      // Naive sum-of-squares method:
      double sum = 0.0, sumOfSquares = 0.0;
      for (int jj = 1; jj <= 3; ++jj) {
        sum += K + jj;
        sumOfSquares += square(K + jj);
      }
      double variance = (3 * sumOfSquares - sum * sum) / square(3);
      if (K <= 1<<25) {
        assertEquals(2/3.0, variance);  // Correct result.
      } else {
        assertEquals(0.0, variance);  // Loss of precision for large K!
      }
    }
  }

  // stolen from distribution_test.cc
  public void testQuantileEstimation() throws Exception {
    // 80 buckets of 10, [0..800).
    Distribution d = new Distribution(Bucketer.fixedWidth(10, 80));

    // Flat PDF => NthPercentile(x%) = x * 8.
    //          => FractionLessThan(x) = x / 800.
    for (int ii = 0; ii < 800; ++ii)
      d.add(ii);

    assertEquals(0.0, d.getNthPercentile(0));
    assertEquals(400.0, d.getMedian());
    for (int ii = 1; ii <= 100; ++ii) {
      assertEquals(8.0, d.getNthPercentile(ii) / ii, 0.0001);
    }
    for (int ii = 0; ii < 800; ++ii) {
      assertEquals(ii, 800 * d.getFractionLessThan(ii), 0.0001);
    }

    // "The quantiles of an empty distribution are the sounds of one
    // hand clapping."
    assertEquals(0.0, new Distribution(Bucketer.DEFAULT).getMedian());
    assertEquals(0.0, new Distribution(Bucketer.DEFAULT).getNthPercentile(25));
    assertEquals(0.0, new Distribution(Bucketer.DEFAULT).getFractionLessThan(0));
    assertEquals(0.0, new Distribution(Bucketer.DEFAULT).getFractionLessThan(10));

    // Handling of negative numbers.  Note, they all appear in the
    // "underflow" bucket.  This means the median and percentiles are
    // accurate so long as they are positive.
    d = new Distribution(Bucketer.fixedWidth(10, 80));
    for (int ii = -100; ii <= 100; ++ii) {
      d.add(ii);
    }

    // Note, the quantiles are the smallest value *larger* than n% of samples.
    assertEquals(0.5,   d.getMedian());           // ok   (ideally 0 + epsilon)
    assertEquals(0.0,   d.getNthPercentile(25));  // hmm.  ideally should be -50
    assertEquals(50.75, d.getNthPercentile(75));  // ok   (ideally 50 + epsilon)

    assertEquals(0.0, d.getFractionLessThan(-50));   // ideally 0.25.
    assertEquals(0.0, d.getFractionLessThan(-0.1));  // ideally 0.5 - epsilon.
    assertEquals(0.4975, d.getFractionLessThan(0), 0.0001);    // ok, 100/201.
    assertEquals(0.7462, d.getFractionLessThan(50), 0.0001);   // ok, 150/201.
    assertEquals(0.9950, d.getFractionLessThan(100), 0.0001);  // ok, 200/201.
    assertEquals(0.9955, d.getFractionLessThan(101), 0.0001);  // ok, interpolation
    assertEquals(1.0, d.getFractionLessThan(500000));          // ok

    d.add(101);  // tilt the median to the positive.
    assertEquals(1.0, d.getMedian());  // ok
    d.add(102);  // and again.
    assertEquals(1.5, d.getMedian());  // ideally 2

    // Handling of overflow bucket.  All percentiles are accurate
    // so long as they are less than the overflow value.
    d = new Distribution(Bucketer.fixedWidth(10, 80));
    for (int ii = 0; ii < 1000; ++ii) {
      d.add(ii);
    }

    assertEquals(500.0, d.getMedian());
    assertEquals(250.0, d.getNthPercentile(25));
    assertEquals(750.0, d.getNthPercentile(75));
    // Any percentile that falls in the overflow bucket reports
    // the maximum value of the last finite bucket
    assertEquals(800.0, d.getNthPercentile(90));

    assertEquals(0.0, d.getFractionLessThan(-50));
    assertEquals(0.0, d.getFractionLessThan(-0.1));
    assertEquals(0.1, d.getFractionLessThan(100));
    assertEquals(0.5, d.getFractionLessThan(500));
    // The upper bound of the last finite bucket should report an
    // accurate value
    assertEquals(0.8, d.getFractionLessThan(800));
    // Anything in the overflow bucket should report 1
    assertEquals(1.0, d.getFractionLessThan(900));

    // Eight buckets, each with (1 << 30) samples. Construct this in proto
    // form, since otherwise we'd have to loop and it would be slow.
    int maxBuckets = 8;

    BucketerProto.Builder bucketerProtoBuilder = BucketerProto.newBuilder();
    bucketerProtoBuilder
        .setNumFiniteBuckets(maxBuckets)
        .setWidth(1)
        .setGrowthFactor(0)
        .setScaleFactor(1.0);

    DistributionProto.Builder proto =
        DistributionProto.newBuilder()
            .setCount(1L << 33)
            .setMean(4.0)
            .setSumOfSquaredDeviation(45097156608.0)
            .setUnderflow(0)
            .setOverflow(0)
            .setBucketer(bucketerProtoBuilder.build());
    for (int bucket = 0; bucket < maxBuckets; ++bucket) {
      proto.addBucket(1 << 30);
    }
    d = Distribution.fromProto(proto.build());
    for (int ii = 0; ii <= maxBuckets; ++ii) {
      assertEquals(ii / 8.0, d.getFractionLessThan(ii), 0.0001);
    }

    // TODO(adonovan):
    // - Test exponential bucketing.
  }

  public void testEquals() {
    Distribution d1 = new Distribution(Bucketer.fixedWidth(10, 80));
    Distribution d2 = new Distribution(Bucketer.fixedWidth(10, 80));
    Distribution d3 = new Distribution(Bucketer.fixedWidth(10, 70));

    assertEquals(d1, d2);
    assertFalse(d1.equals(d3));

    // Flat PDF => NthPercentile(x%) = x * 8.
    for (int ii = 0; ii < 800; ++ii) {
      d1.add(ii);
      d2.add(ii);
      assertEquals(d1, d2);
    }

    d1.add(1);
    assertFalse(d1.equals(d2));
  }

  public void testToString() {
    Distribution d1 = new Distribution(Bucketer.fixedWidth(10, 80));

    assertEquals("Distribution(n=0, mean=0.000, stddev=0.000)", d1.toString());
    d1.add(20);
    assertEquals("Distribution(n=1, mean=20.00, stddev=0.000)", d1.toString());
    d1.add(40);
    assertEquals("Distribution(n=2, mean=30.00, stddev=10.00)", d1.toString());
  }

  public void testToDebugString() {
    Distribution d1 = new Distribution(Bucketer.fixedWidth(10, 80));

    assertEquals("Distribution{bucketer=Bucketer(width=10.0, growthFactor=0.0, scaleFactor=1.0, " +
            "maxBuckets=80), count=0, mean=0.0, stddev=0.0, overflow=0, underflow=0, buckets=[]}",
        d1.toDebugString());
    d1.add(20);
    assertEquals("Distribution{bucketer=Bucketer(width=10.0, growthFactor=0.0, scaleFactor=1.0, " +
            "maxBuckets=80), count=1, mean=20.0, stddev=0.0, overflow=0, underflow=0, " +
            "buckets=[0, 0, 1]}",
        d1.toDebugString());
    d1.add(4);
    assertEquals("Distribution{bucketer=Bucketer(width=10.0, growthFactor=0.0, scaleFactor=1.0, " +
            "maxBuckets=80), count=2, mean=12.0, stddev=8.0, overflow=0, " +
            "underflow=0, buckets=[1, 0, 1]}",
        d1.toDebugString());
  }

  public void testFromProtoWithoutABucketerField() throws Exception {
    // proto msg p does not have bucketer field set
    BucketerProto bucketerProto = BucketerProto.newBuilder()
        .setNumFiniteBuckets(10)
        .setWidth(0)
        .setGrowthFactor(2)
        .setScaleFactor(1.0).build();

    DistributionProto p = DistributionProto.newBuilder()
        .setCount(10)
        .setMean(42.0)
        .setSumOfSquaredDeviation(400)
        .setBucketer(bucketerProto)
        .addBucket(-3)
        .addBucket(5)
        .addBucket(4)
        .addBucket(1).build();

    // expected to use deprecated width, growth_factor, and max_buckets fields of distribution proto
    // to create a bucketer.
    Distribution d = Distribution.fromProto(p);
    Bucketer b = d.getBucketer();
    assertEquals(10, b.getMaxBuckets());
    assertEquals(0.0, b.getWidthCoefficient());
    assertEquals(2.0, b.getGrowthFactor());
  }

  public void testSumOfSquaredDeviationDoesNotBecomeNegative() {
    for (int count = 1; count < 1000; count++) {
      Distribution d = new Distribution(Bucketer.fixedWidth(10, 20));
      d.addMultiple(5.0, count);
      assertThat(d.getSumOfSquaredDeviation()).isWithin(0.0).of(0.0);
      d.addMultiple(5.0, 1);
      assertThat(d.getSumOfSquaredDeviation()).isWithin(0.0).of(0.0);
    }
  }
}