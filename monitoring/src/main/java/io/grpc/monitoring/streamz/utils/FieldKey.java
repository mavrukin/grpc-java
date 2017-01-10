package io.grpc.monitoring.streamz.utils;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Represents an ordered set of keys used to reference an individual metric cell.
 *
 * @author konigsberg@google.com (Robert Konigsberg)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class FieldKey {
  private static final Set<Class<?>> FIELD_CLASSES =
      ImmutableSet.<Class<?>>of(Integer.class, String.class, Boolean.class);

  /**
   * Create a new instance.
   *
   * @param fieldValues a list of field values.
   */
  public static FieldKey of(List<Object> fieldValues) {
    return new FieldKey(fieldValues.toArray());
  }

  /**
   * Create a new instance.
   *
   * @param fieldValues the list of field values.
   */
  public static FieldKey of(Object... fieldValues) {
    return new FieldKey(fieldValues);
  }

  private final Object[] fieldValues;
  private final int hashCode;

  private FieldKey(Object[] fieldValues) {
    this.fieldValues = fieldValues;

    for (int index = 0; index < fieldValues.length; index++) {
      // replace enums with their corresponding string value
      fieldValues[index] =
          (fieldValues[index] instanceof Enum) ? fieldValues[index].toString() : fieldValues[index];

      Object fieldValue = fieldValues[index];
      if (!FIELD_CLASSES.contains(fieldValue.getClass())) {
        throw new IllegalArgumentException(
            String.format("Type %s of field %d (%s) is not allowed", fieldValue.getClass(), index,
                fieldValue));
      }
    }
    this.hashCode = Arrays.hashCode(fieldValues); // eager, since always needed.
  }

  /**
   * Return the number of elements in this key.
   */
  public int size() {
    return fieldValues.length;
  }

  /**
   * Return the value of the field at the specified zero-based index.
   */
  Object getValue(int index) {
    return fieldValues[index];
  }

  @Override
  public boolean equals(Object that) {
    if (that == this) {
      return true;
    }
    return that instanceof FieldKey &&
        Arrays.equals(this.fieldValues, ((FieldKey) that).fieldValues);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return Arrays.toString(fieldValues);
  }
}