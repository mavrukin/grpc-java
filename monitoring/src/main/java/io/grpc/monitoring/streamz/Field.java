package io.grpc.monitoring.streamz;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;

/**
 * Definition of a metric field.  Fields are immutable and may be used  repeatedly across metrics.
 *
 * @param <T> the type of values that this field accepts.  This may differ from how the field is
 * exported to the streamz monitoring system, as in the case of an Enum-as-String definition
 *
 * @author konigsberg@google.com (Robert Konigsberg)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public class Field<T> {
  private static final ImmutableSet<Class<?>> NATIVE_FIELD_TYPES =
      ImmutableSet.<Class<?>>of(Integer.class, String.class, Boolean.class);

  /**
   * The name of the field
   */
  private final String name;

  /**
   * The type of field as visible to the caller.  This may differ from the nativeType, as in the
   * case of an Enum-as-String definition.
   */
  private final Class<T> inputType;

  /**
   * The type of the field as exported to the Streamz data model.
   */
  private Class<?> nativeType;

  /**
   * A description of the field.
   */
  private final String description;

  /**
   * Create a field of String type.
   *
   * @param name the name of the field
   * @throws NullPointerException when {@code name} is {@code null}.
   */
  public static Field<String> ofString(String name) {
    return ofString(name, null);
  }

  /**
   * Create a field of String type
   * @param name the name of the field
   * @param description a textual description of the field, such as "the column family being read".
   * May be {@code null} but instead of supplying null descriptions, use {@link #ofString(String)}.
   * @throws NullPointerException when {@code name} is {@code null}
   */
  public static Field<String> ofString(String name, String description) {
    return new Field<String>(name, String.class, description);
  }

  /**
   * Create a field of Integer type.
   * @param name the name of the field
   * @throws NullPointerException when {@code code} is {@code null}
   */
  public static Field<Integer> ofInteger(String name) {
    return ofInteger(name, null);
  }

  /**
   * Create a field of Integer type.
   * @param name the name of the field
   * @param description a textual description of the field, such as "the shard number being tested".
   * May be {@code null} but instead of supplying null descriptions, use {@link #ofInteger(String)}.
   * @throws NullPointerException when {@code name} is {@code null}
   */
  public static Field<Integer> ofInteger(String name, String description) {
    return new Field<Integer>(name, Integer.class, description);
  }

  /**
   * Create a field of Boolean type.
   * @param name the name of the field
   * @throws NullPointerException when {@code name} is {@code null}
   */
  public static Field<Boolean> ofBoolean(String name) {
    return ofBoolean(name, null);
  }

  /**
   * Create a field of Boolean type.
   * @param name the name of the field
   * @param description  a textual description of the field, such as
   * "whether the request succeeded".  May be {@code null} but instead of supplying null
   * descriptions, use {@link #ofBoolean(String)}.
   * @throws NullPointerException when {@code name} is {@code null}
   */
  public static Field<Boolean> ofBoolean(String name, String description) {
    return new Field<Boolean>(name, Boolean.class, description);
  }

  /**
   * Create a new field of an Enum type, which will be exported to the streamz data model as
   * Strings.  The exported Strings will be obtained by calling {@link Enum#toString()}.
   *
   * <p>This kind of field is a useful way to enforce the rule that streamz metric fields should
   * take on only a predictably small number of values.</p>
   *
   * @param enumType the type of the enumeration accepted by this field
   * @param name the name of the field
   * @throws NullPointerException when {@code enumType} or {@code name} is {@code null}
   */
  public static <E extends Enum<E>> Field<E> ofEnumAsString(Class<E> enumType, String name) {
    return ofEnumAsString(enumType, name, null);
  }

  /**
   * Create a new field of an Enum type, which will be exported to the streamz data model as
   * Strings.  The exported Strings will be obtained by calling {@link Enum#toString()}.
   *
   * <p>This kind of field is a useful way to enforce the rule that streamz metric fields should
   * take on only a predictably small number of values.</p>
   *
   * @param enumType the type of the enumeration accepted by this field
   * @param name the name of the field
   * @param description a textual description of the field, such as "the column family being read".
   * May be {@code null} but instead of supplying null descriptions, use
   * {@link #ofEnumAsString(Class, String)}
   * @throws NullPointerException when {@code enumType} or {@code name} is {@code null}.
   */
  public static <E extends Enum<E>> Field<E> ofEnumAsString(Class<E> enumType, String name,
      String description) {
    return new Field<E>(name, enumType, description);
  }

  /**
   * Package-private for internal callers.
   *
   * @throws NullPointerException when any of {@code name} or {@code inputType} is {@code null}
   * @throws IllegalArgumentException when the field name doesn't match the field name pattern
   * or when inputType is an unsuitable class.
   */
  private Field(String name, Class<T> inputType, String description) {
    this.name = Preconditions.checkNotNull(name);
    this.inputType = Preconditions.checkNotNull(inputType);
    this.description = description;
    this.nativeType = inputType.isEnum() ? String.class : inputType;
    if(!NATIVE_FIELD_TYPES.contains(nativeType)) {
      throw new IllegalArgumentException("Invalid field type " + inputType + "; must be one of "
      + NATIVE_FIELD_TYPES + " or an enum");
    }
  }

  /**
   * @return the name of the field as exported to the streamz data model.
   */
  private String getName() {
    return name;
  }

  /**
   * @return the type of the field as visible to the caller.
   */
  public Class<T> getType() {
    return inputType;
  }

  /**
   * @return the streamz-native type
   */
  Class<?> getNativeType() {
    return nativeType;
  }

  /**
   * @return the description of this field, may be {@code null} of it wasn't set
   */
  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    }

    if (object instanceof Field) {
      Field<?> that = (Field<?>)object;
      return this.inputType == that.inputType &&
          this.nativeType == that.nativeType &&
          this.name.equals(that.name) &&
          Objects.equal(this.description, that.description);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return String.format("(%s, %s, %s, %s)", name, inputType, nativeType, description);
  }
}