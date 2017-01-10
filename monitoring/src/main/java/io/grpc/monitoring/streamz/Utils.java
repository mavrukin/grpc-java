package io.grpc.monitoring.streamz;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * Various utility functions.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
final class Utils {

  private Utils() {}

  private static final Clock defaultSystemClock = new SystemClock();
  private static AtomicReference<Clock> clockRef = new AtomicReference<Clock>();

  static Clock getClock() {
    clockRef.compareAndSet(null, defaultSystemClock);
    return clockRef.get();
  }
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

  static long getCurrentTimeMicros() {
    return TimeUnit.MILLISECONDS.toMicros(getClock().now().getMillis());
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

  /**
   * Returns the name of the JVM's main class, or the string "unknown" in the
   * case where the main thread has already terminated.
   */
  // TODO(adonovan): move this beneath com.google.common
  static String getMainClassName() {
    // If main has started, the topmost active method on a given stack is
    // called "run" iff the class is a subclass of Thread, or "main" for the
    // sole main class.
    if (!runningOnAppEngine()) {
      // Thread.getAllStackTraces is unavailable on AppEngine.
      for (StackTraceElement[] stackTrace : Thread.getAllStackTraces().values()) {
        if (stackTrace.length > 0) {
          StackTraceElement stackStart = stackTrace[stackTrace.length - 1];
          if (stackStart.getMethodName().equals("main")) {
            return stackStart.getClassName();
          }
        }
      }
      // If main hasn't started yet, the main class must still be initializing:
      for (StackTraceElement[] stackTrace : Thread.getAllStackTraces().values()) {
        if (stackTrace.length > 0) {
          StackTraceElement stackStart = stackTrace[stackTrace.length - 1];
          if (stackStart.getMethodName().equals("<clinit>")) {
            return stackStart.getClassName();
          }
        }
      }
    }
    return "unknown";
  }

}