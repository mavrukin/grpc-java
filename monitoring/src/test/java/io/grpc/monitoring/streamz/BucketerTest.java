package io.grpc.monitoring.streamz;

import junit.framework.TestCase;

import io.grpc.monitoring.streamz.proto.BucketerProto;

public class BucketerTest extends TestCase {
  // From TestBucketer() in m/s/internal/distribution_test.cc.
  public void testBucketerFunctions() throws Exception {
    // Default bucketing: log4 over the interval [0..2^32)
    Bucketer b1 = Bucketer.DEFAULT;
    assertEquals(17,             b1.getMaxBuckets());
    assertEquals(0.0,            b1.getBucketMinimum(0)); // zero gets its own bucket
    assertEquals(1.0,            b1.getBucketMinimum(1));
    assertEquals(4.0,            b1.getBucketMinimum(2)); // [4,16)
    assertEquals(16.0,           b1.getBucketMaximum(2));
    assertEquals(16.0,           b1.getBucketMinimum(3));
    assertEquals(64.0,           b1.getBucketMinimum(4));
    assertEquals(1.0 * (1<<28),  b1.getBucketMinimum(15));
    assertEquals(1.0 * (1<<30),  b1.getBucketMinimum(16));
    assertEquals(1.0 * (1L<<32), b1.getBucketMaximum(16));

    // Explicit decimal bucketing.
    Bucketer b2 = Bucketer.powersOf(10);
    assertEquals(   11, b2.getMaxBuckets());
    assertEquals(  0.0, b2.getBucketMinimum(0));
    assertEquals(  1.0, b2.getBucketMinimum(1));
    assertEquals( 10.0, b2.getBucketMinimum(2));
    assertEquals(100.0, b2.getBucketMinimum(3));
    assertEquals(1E3,   b2.getBucketMaximum(3)); // [100.0, 1000)
    assertEquals(1E3,   b2.getBucketMinimum(4));
    assertEquals(1E4,   b2.getBucketMinimum(5));
    assertEquals(1E7,   b2.getBucketMinimum(8));
    assertEquals(1E8,   b2.getBucketMinimum(9));
    assertEquals(1E9,   b2.getBucketMinimum(10));
    assertEquals(1E10,  b2.getBucketMaximum(10));

    // Scaled explicit decimal bucketing.
    Bucketer b22 = Bucketer.scaledPowersOf(10, 3.0, 1e10);
    assertEquals(   11, b22.getMaxBuckets());
    assertEquals(  0.0, b22.getBucketMinimum(0));
    assertEquals(  3.0, b22.getBucketMinimum(1));
    assertEquals( 30.0, b22.getBucketMinimum(2));
    assertEquals(300.0, b22.getBucketMinimum(3));
    assertEquals(3E3,   b22.getBucketMaximum(3)); // [100.0, 1000)
    assertEquals(3E3,   b22.getBucketMinimum(4));
    assertEquals(3E4,   b22.getBucketMinimum(5));
    assertEquals(3E7,   b22.getBucketMinimum(8));
    assertEquals(3E8,   b22.getBucketMinimum(9));
    assertEquals(3E9,   b22.getBucketMinimum(10));
    assertEquals(3E10,  b22.getBucketMaximum(10));

    // Fixed-width decade bucketing of two centuries.
    Bucketer b3 = Bucketer.fixedWidth(10, 20);
    assertEquals(    20, b3.getMaxBuckets());
    assertEquals(   0.0, b3.getBucketMinimum(0));
    assertEquals(  10.0, b3.getBucketMinimum(1));
    assertEquals(  20.0, b3.getBucketMinimum(2));
    assertEquals( 100.0, b3.getBucketMinimum(10));
    assertEquals( 180.0, b3.getBucketMinimum(18));
    assertEquals( 190.0, b3.getBucketMinimum(19));
    assertEquals( 200.0, b3.getBucketMaximum(19));

    // Custom bucketing with given buckets boundaries.
    double[] bounds = {1.0, 2.0, 3.0, 10000.0};
    Bucketer b4 = Bucketer.custom(bounds);
    assertEquals(      3, b4.getMaxBuckets());
    assertEquals(    1.0, b4.getBucketMinimum(0));
    assertEquals(    2.0, b4.getBucketMinimum(1));
    assertEquals(    3.0, b4.getBucketMinimum(2));
    assertEquals(10000.0, b4.getBucketMinimum(3));

    // Minimum bucketer: just a single bucket.
    Bucketer b5 = Bucketer.NONE;
    assertEquals(               1, b5.getMaxBuckets());
    assertEquals(             0.0, b5.getBucketMinimum(0));
    assertEquals(Double.MAX_VALUE, b5.getBucketMaximum(0));

    // Too many buckets
    Bucketer b6 = Bucketer.fixedWidth(10, 20000);
    assertEquals(            5000, b6.getMaxBuckets());
    assertEquals(         50000.0, b6.getBucketMinimum(5000));
  }

  public void testFindBucket() throws Exception {
    Bucketer b = Bucketer.powersOf(10);
    assertEquals(Bucketer.UNDERFLOW_BUCKET,  b.findBucket(-1));
    assertEquals(0,  b.findBucket(0));
    assertEquals(0,  b.findBucket(0.123));
    assertEquals(1,  b.findBucket(1.0));
    assertEquals(1,  b.findBucket(2.0));
    assertEquals(1,  b.findBucket(9.999));
    assertEquals(2,  b.findBucket(10));
    assertEquals(2,  b.findBucket(99));
    assertEquals(10, b.findBucket(1E9));
    assertEquals(10, b.findBucket(1L << 32));
    assertEquals(Bucketer.OVERFLOW_BUCKET, b.findBucket(1E10));
    assertEquals(Bucketer.OVERFLOW_BUCKET, b.findBucket(1E35));

    // Fixed-width decade bucketing of two centuries.
    Bucketer b2 = Bucketer.fixedWidth(10, 20);
    assertEquals(Bucketer.OVERFLOW_BUCKET, b2.findBucket(15000));
  }

  public void testCanonical() throws Exception {
    assertSame(Bucketer.DEFAULT, Bucketer.powersOf(4));
    assertSame(Bucketer.powersOf(10), Bucketer.powersOf(10));
    assertSame(Bucketer.scaledPowersOf(10, 100, 100), Bucketer.scaledPowersOf(10, 100, 100));
    double[] bounds = {-1.0, 0.0, 1000.0, 10000.88};
    assertSame(Bucketer.custom(bounds), Bucketer.custom(bounds));
  }

  public void testCoefficientAccessors() throws Exception {
    Bucketer b = Bucketer.fixedWidth(3.0, 12);
    assertEquals(3.0,   b.getWidthCoefficient());
    assertEquals(0.0, b.getGrowthFactor());
    assertEquals(12,    b.getMaxBuckets());
  }

  public void testCreationFailure() throws Exception {
    try {
      Bucketer.fixedWidth(0.0, -1);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Histograms must have at least 1 bucket: -1", e.getMessage());
    }
    try {
      Bucketer.fixedWidth(0.0, 0);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Histograms must have at least 1 bucket: 0", e.getMessage());
    }
    try {
      Bucketer.powersOf(-3.1);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("growthFactor must be >= 1.01, is -3.1", e.getMessage());
    }
    try {
      Bucketer.powersOf(.99);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("growthFactor must be >= 1.01, is 0.99", e.getMessage());
    }
    try {
      Bucketer.fixedWidth(-1.0, 10);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Negative width coefficients are not allowed: -1.0", e.getMessage());
    }
    try {
      Bucketer.powersOf(1.0); // Must be >= than 1.01;
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("growthFactor must be >= 1.01, is 1.0", e.getMessage());
    }
    try {
      Bucketer.scaledPowersOf(1.1, -.1, 100.0);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("scaleFactor must be > 0.0, is -0.1", e.getMessage());
    }
    try {
      Bucketer.scaledPowersOf(1.1, 1.999, -1.0);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("maxValue must be >= 0.0, is -1.0", e.getMessage());
    }
    try {
      Bucketer.powersOf(1.009); // Must be >= than 1.01;
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("growthFactor must be >= 1.01, is 1.009", e.getMessage());
    }
    try {
      double[] bounds = new double[0];
      Bucketer.custom(bounds);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Custom bounds must have at least 2 buckets, is 0", e.getMessage());
    }
    try {
      double[] bounds = {1.0};
      Bucketer.custom(bounds);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Custom bounds must have at least 2 buckets, is 1", e.getMessage());
    }
    try {
      double[] bounds = {0.1, 0.3, 0.2};
      Bucketer.custom(bounds);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Custom bounds must be monotonically increasing", e.getMessage());
    }
    try {
      BucketerProto.Builder bldr = BucketerProto.newBuilder();
      bldr.setNumFiniteBuckets(2);
      BucketerProto p = bldr.build();
      Bucketer b = Bucketer.fromProto(p);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Must give a growth factor or a width.", e.getMessage());
    }
    try {
      BucketerProto.Builder bldr = BucketerProto.newBuilder();
      bldr.setNumFiniteBuckets(2);
      bldr.setWidth(2.0);
      bldr.setScaleFactor(2.0);
      BucketerProto p = bldr.build();
      Bucketer b = Bucketer.fromProto(p);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Cannot give scale factor with fixed width", e.getMessage());
    }
    try {
      BucketerProto.Builder bldr = BucketerProto.newBuilder();
      bldr.setNumFiniteBuckets(2);
      bldr.setGrowthFactor(2.0);
      bldr.setScaleFactor(0.0);
      BucketerProto p = bldr.build();
      Bucketer b = Bucketer.fromProto(p);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Scale factors of 0.0 or less are not allowed: 0.0", e.getMessage());
    }
  }

  public void testProtoEncodingAndDecoding() throws Exception {
    // Encoding and decoding custom bucketer
    double[] bounds = {0.0, 2.71828, 3.14159, 6.218};
    Bucketer b1 = Bucketer.custom(bounds);
    BucketerProto p1 = b1.toProto();

    assertEquals(p1.getWidth(), 0.0d);
    assertEquals(p1.getGrowthFactor(), 0.0d);
    assertEquals(p1.getNumFiniteBuckets(), 0);
    assertEquals(p1.getLowerBoundsCount(), 4);
    assertEquals(p1.getLowerBounds(0), 0.0);
    assertEquals(p1.getLowerBounds(1), 2.71828);
    assertEquals(p1.getLowerBounds(2), 3.14159);
    assertEquals(p1.getLowerBounds(3), 6.218);

    Bucketer b2 = Bucketer.fromProto(p1);
    assertEquals(b1, b2);
    BucketerProto p2 = b2.toProto();
    assertEquals(p1, p2);

    // Encoding and decoding non-custom bucketer
    Bucketer b3 = Bucketer.fixedWidth(5.0, 20);
    BucketerProto p3 = b3.toProto();

    assertEquals(p3.getWidth(), 5.0);
    assertEquals(p3.getGrowthFactor(), 0.0);
    assertEquals(p3.getNumFiniteBuckets(), 20);
    assertEquals(p3.getLowerBoundsCount(), 0);

    Bucketer b4 = Bucketer.fromProto(p3);
    assertEquals(b3, b4);
    BucketerProto p4 = b4.toProto();
    assertEquals(p3, p4);

    Bucketer b5 = Bucketer.scaledPowersOf(2.0, 3.0, 10);
    BucketerProto p5 = b5.toProto();

    assertEquals(0.0, p5.getWidth());
    assertEquals(2.0, p5.getGrowthFactor());
    assertEquals(3.0, p5.getScaleFactor());
    assertEquals(3, p5.getNumFiniteBuckets());
    assertEquals(0, p5.getLowerBoundsCount());

    Bucketer b6 = Bucketer.fromProto(p5);
    assertEquals(b5, b6);
    BucketerProto p6 = b6.toProto();
    assertEquals(p5, p6);
  }
}