package io.grpc.monitoring.streamz;

import java.util.Arrays;
import java.util.IllegalFormatException;
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
}