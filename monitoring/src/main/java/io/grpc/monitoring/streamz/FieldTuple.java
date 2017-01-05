package io.grpc.monitoring.streamz;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Arrays;

/**
 * FieldTuple: a trivial immutable list of field values. Can be used as a field
 * in a map.  Just like ImmutableList<Object>, except it doesn't copy its
 * argument list.  This allows a faster common-case map lookup.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class FieldTuple implements Iterable<Object> {
  public static final FieldTuple NO_FIELDS = new FieldTuple();

  private final Object[] fieldValues;
  private final int hashCode;

  FieldTuple(Object... fieldValues) {
    this.fieldValues = fieldValues;
    this.hashCode = Arrays.hashCode(fieldValues);
  }

  public int length() {
    return fieldValues.length;
  }

  @Override
  public boolean equals(Object that) {
    return that instanceof FieldTuple &&
        Arrays.equals(this.fieldValues, ((FieldTuple)that).fieldValues);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return Arrays.toString(fieldValues);
  }

  @Override
  public UnmodifiableIterator<Object> iterator() {
    return Iterators.forArray(fieldValues);
  }

  public Object get(int i) {
    return fieldValues[i];
  }
}