package io.grpc.monitoring.streamz.utils;

import com.google.common.base.Preconditions;
import java.util.List;

/**
 * Represents a rule for matching {@link FieldKey}s.
 */
public class FieldKeyMatcher {

  /**
   * Matches any field value.
   */
  public static final Object WILDCARD = new Object();

  /**
   * Creates a new matcher that requires each value of the FieldKey to match the corresponding
   * value from the specified list. {@link FieldKeyMatcher#WILDCARD} may be used to allow any
   * value for the corresponding field.
   */
  public static FieldKeyMatcher matcher(List<Object> fieldValues) {
    return new FieldKeyMatcher(fieldValues.toArray());
  }

  /**
   * Creates a new matcher that requires each value of the FieldKey to match the corresponding
   * value from the argument list. {@code WILDCARD} may be used to allow any value for the
   * corresponding field.
   */
  public static FieldKeyMatcher matcher(Object... fieldValues) {
    return new FieldKeyMatcher(fieldValues);
  }

  /**
   * Returns whether the specified {@code FieldKey} is matched.
   *
   * @throws IllegalArgumentException if the specified FieldKey has a different size than the
   * matcher.
   */
  public boolean matches(FieldKey fieldKey) {
    Preconditions.checkArgument(fieldValues.length == fieldKey.size(),
        "FieldKeyMatcher size of %s does not match FieldKey size of %s", fieldValues.length, fieldKey.size());

    for (int i = 0; i < fieldValues.length; i++) {
      if (fieldValues[i] == WILDCARD) {
        continue;
      }
      if (!fieldValues[i].equals(fieldKey.getValue(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns whether any of the specified {@code FieldKey}s are matched.
   */
  public boolean matchesAny(Iterable<FieldKey> fieldKeys) {
    for (FieldKey fieldKey : fieldKeys) {
      if (matches(fieldKey)) {
        return true;
      }
    }
    return false;
  }

  private FieldKeyMatcher(Object[] fieldValues) {
    this.fieldValues = fieldValues;
    for (int index = 0; index < fieldValues.length; index++) {
      // Replace enums with their corresponding string value.
      fieldValues[index] =
          (fieldValues[index] instanceof Enum) ? fieldValues[index].toString() : fieldValues[index];
    }
  }

  private final Object[] fieldValues;
}