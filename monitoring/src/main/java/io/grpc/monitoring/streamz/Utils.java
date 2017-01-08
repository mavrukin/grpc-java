package io.grpc.monitoring.streamz;

import com.google.inject.ImplementedBy;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class Utils {
  /**
   * Safely formats a string with {@link String#format(String, Object[])},
   * and gaurantees not to throw an exception, but instead returns a slightly
   * different message.
   *
   * @param format the format string
   * @param args array of parameters for the format string
   */
  static String formatSafely(String format, @Nullable Object... args) {
    try {
      return String.format(format, args);
    } catch (IllegalFormatException e) {
      return String.format("Failed to format message: \"%s\", args: %s", format,
          (args != null) ? Arrays.toString(args) : "null");
    } catch (Exception e) {
      // such as a failure during toString() on one of the arguments
      return String.format("Failed to format message: \"%s\"", format);
    }
  }

  static String getHostname() {
    try {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  /**
   * Returns {@code true} if running on Google AppEngine (development or production environment).
   */
  static boolean runningOnAppEngine() {
    // See http://code.google.com/appengine/docs/java/runtime.html#The_Environment.
    return System.getProperty("com.google.appengine.runtime.environment") != null;
  }

  // TODO(avrukin) This does not implement the "correct" clock, fix this prior to prod

  static long getCurrentTimeMicros() {
    return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
  }

  private static long processStartTime = -1;

  public static synchronized long getProcessStartTimeMicros() {
    if (runningOnAppEngine()) {
      // ManagementFactory is unavailable on AppEngine.
      if (processStartTime == -1) {
        processStartTime = getCurrentTimeMicros();
      }
      return  processStartTime;
    }
    return TimeUnit.MILLISECONDS.toMicros(ManagementFactory.getRuntimeMXBean().getStartTime());
  }
}