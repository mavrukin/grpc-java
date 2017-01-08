package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.grpc.monitoring.streamz.proto.DistributionProto;
import io.grpc.monitoring.streamz.proto.StreamValue;
import io.grpc.monitoring.streamz.proto.TypedMessage;
import io.grpc.monitoring.streamz.proto.Types.EncodedValueType;
import io.grpc.monitoring.streamz.proto.Types.ValueTypeDescriptor;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper functions for treating Metric value types generically.
 *
 * <p>Stores the default value for newly-created cells. There is
 * one instance of this class for each metric.
 *
 * <p>Almost all types supported by Metric are immutable (Java's built-in
 * numeric types, Strings, Enums, and proto3 Messages).  Only Distribution is
 * mutable; which is why this class overrides {@link #clone(Object)}.
 *
 * @author adonovan@google.com (Alan Donovan)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
abstract class ValueTypeTraits<V> {

    private final String name;
    private V defaultValue;
    private final Class<V> type;
    private final EncodedValueType encodedType;

    private ValueTypeTraits(String name, EncodedValueType encodedType, Class<V> type,
                            V defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.encodedType = encodedType;
        this.type = type;
    }

    /**
     * Increment the value encoded in {@code holder} for this type by {@code step} step atomically.
     *
     * <p>{@code step} may be truncated or rounded if its type is different to
     * that of {@code value}.
     *
     * @throws UnsupportedOperationException if the type is not numeric.
     */
    void incrementBy(AtomicLong holder, Number step) {
        throw new UnsupportedOperationException("incrementBy() not supported for " + this);
    }

    /**
     * Returns {@code true} iff value type can be encoded as long value.
     */
    boolean isConvertableToLong() {
        return false;
    }

    /**
     * Store {@code value}'s long-encoded value in {code holder}. {@code value} is expected to be
     * non-null.
     *
     * @throws UnsupportedOperationException if the type is not primitive or enum.
     */
    void set(AtomicLong holder, V value) {
        throw new UnsupportedOperationException("set() not supported for " + this);
    }

    /** Encodes {@code value} as long. {@code value} is expected to be non-null}. */
    long toLong(V value) {
        throw new UnsupportedOperationException("toLong() not supported for " + this);
    }

    /**
     * Decodes {@code value} from Long representation to the original one.
     * @throws UnsupportedOperationException if the type is not primitive or enum.
     */
    V fromLong(long value) {
        throw new UnsupportedOperationException("fromLong() not supported for " + this);
    }

    /**
     * Returns the Streamz protocol encoding of this type.
     */
    final EncodedValueType getEncodedType() {
        return encodedType;
    }

    /**
     * Returns the default value of this type.
     */
    final V getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value for new cells of this type.
     */
    final void setDefaultValue(V value) {
        this.defaultValue = value;
    }

    /**
     * Populates {@code streamValue} with {@code value}, which must be non-null.
     */
    abstract void toStreamValue(StreamValue.Builder streamValue, V value);

    /**
     * Populates {@code vtd} with information about this type.
     */
    void toValueTypeDescriptor(ValueTypeDescriptor.Builder vtd,
                               @SuppressWarnings("unused") boolean includeMetadata) {
        vtd.setValueType(getEncodedType());
    }

    /**
     * If the type is a protocol message, gets its descriptor.
     * @return null if not a protocol message.
     */
    public Descriptor getProtoDescriptor() {
        return null;
    }

    /*
     TODO(avrukin) - Remove reference to HTML because we want to make sure that this is nice and clean, just API
     no presentation.
    public String toHtml(V value) {
        if (value == null) {
            return null;
        }
        return HtmlEscapers.htmlEscaper().escape(value.toString());
    } */

    /**
     * Returns an equivalent value of the same type such that changes to the
     * result do not affect the argument, and vice versa.  For immutable types,
     * the result and argument may be the same; this is the default
     * implementation.  For other types, a copy is required; this method must be
     * redefined in that case.  {@code value} may be null.
     */
    V clone(V value) {
        return value;
    }

    /**
     * Returns a user-friendly name for the type.
     */
    @Override
    public String toString() {
        return name;
    }

    private static ValueTypeTraits<Boolean> createBoolean() {
        return new ValueTypeTraits<Boolean>(
                "boolean", EncodedValueType.VALUETYPE_BOOL, Boolean.class, Boolean.FALSE) {
            @Override
            void toStreamValue(StreamValue.Builder streamValue, Boolean value) {
                streamValue.setBoolValue(value);
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, Boolean value) {
                holder.set(value ? 1 : 0);
            }

            @Override long toLong(Boolean value) { return value ? 1 : 0; }

            @Override Boolean fromLong(long value) { return value != 0; }
        };
    }

    private static ValueTypeTraits<Integer> createInteger() {
        return new ValueTypeTraits<Integer>("int", EncodedValueType.VALUETYPE_INT64, Integer.class, 0) {
            @Override
            void incrementBy(AtomicLong holder, Number step) {
                holder.addAndGet(step.intValue());
            }

            @Override
            void toStreamValue(StreamValue.Builder streamValue, Integer value) {
                streamValue.setInt64Value(value);
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, Integer value) {
                holder.set((long) value);
            }

            @Override long toLong(Integer value) { return (long) value; }

            @Override Integer fromLong(long value) { return (int) value; }
        };
    }

    private static ValueTypeTraits<Long> createLong() {
        return new ValueTypeTraits<Long>("long", EncodedValueType.VALUETYPE_INT64, Long.class, 0L) {
            @Override
            void incrementBy(AtomicLong holder, Number step) {
                holder.addAndGet(step.longValue());
            }

            @Override
            void toStreamValue(StreamValue.Builder streamValue, Long value) {
                streamValue.setInt64Value(value);
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, Long value) {
                holder.set(value);
            }

            @Override long toLong(Long value) { return value; }

            @Override Long fromLong(long value) { return value; }
        };
    }

    private static ValueTypeTraits<Float> createFloat() {
        return new ValueTypeTraits<Float>("float", EncodedValueType.VALUETYPE_DOUBLE, Float.class,
                0.0f) {
            @Override
            void incrementBy(AtomicLong holder, Number step) {
                long current;
                long changed;
                do {
                    current = holder.get();
                    changed = Double.doubleToRawLongBits(
                            (float) Double.longBitsToDouble(current) + step.floatValue());
                } while (!holder.compareAndSet(current, changed));
            }

            @Override
            void toStreamValue(StreamValue.Builder streamValue, Float value) {
                streamValue.setDoubleValue(value);
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, Float value) {
                holder.set(Double.doubleToRawLongBits((double) value));
            }

            @Override long toLong(Float value) { return Double.doubleToRawLongBits((double) value); }

            @Override Float fromLong(long value) { return (float) Double.longBitsToDouble(value); }
        };
    }

    private static ValueTypeTraits<Double> createDouble() {
        return new ValueTypeTraits<Double>("double", EncodedValueType.VALUETYPE_DOUBLE, Double.class,
                0.0) {
            @Override
            void incrementBy(AtomicLong holder, Number step) {
                long current;
                long changed;
                do {
                    current = holder.get();
                    changed = Double.doubleToRawLongBits(
                            Double.longBitsToDouble(current) + step.doubleValue());
                } while (!holder.compareAndSet(current, changed));
            }

            @Override
            void toStreamValue(StreamValue.Builder streamValue, Double value) {
                streamValue.setDoubleValue(value);
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, Double value) {
                holder.set(Double.doubleToRawLongBits(value));
            }

            @Override long toLong(Double value) { return Double.doubleToRawLongBits(value); }

            @Override Double fromLong(long value) { return Double.longBitsToDouble(value); }
        };
    }

    private static ValueTypeTraits<String> createString() {
        return new ValueTypeTraits<String>("string", EncodedValueType.VALUETYPE_STRING, String.class,
                "") {
            @Override
            void toStreamValue(StreamValue.Builder streamValue, String value) {
                streamValue.setStringValue(ByteString.copyFromUtf8(value));
            }
        };
    }

    private static ValueTypeTraits<Void> createVoid() {
        return new ValueTypeTraits<Void>("void", EncodedValueType.VALUETYPE_VOID, Void.class, null) {
            @Override
            void toStreamValue(StreamValue.Builder streamValue, Void value) {
                // (do nothing)
            }
            /*
            TODO(avrukin) Reference to HTML
            @Override public String toHtml(Void value) {
                return HtmlEscapers.htmlEscaper().escape("<Void>");
            } */
        };
    }

    private static ValueTypeTraits<Distribution> createDistribution(final Distribution defaultValue) {
        return new ValueTypeTraits<Distribution>("Distribution",
                EncodedValueType.VALUETYPE_DISTRIBUTION,
                Distribution.class,
                defaultValue) {
            @Override void toStreamValue(StreamValue.Builder streamValue, Distribution value) {
                streamValue.setDistributionValue(value.toProto());
            }
            @Override Distribution clone(Distribution value) {
                return value == null ? value : value.copy(); // (mutable type)
            }
            @Override public Descriptor getProtoDescriptor() {
                return DistributionProto.getDescriptor();
            }
            /*
            TODO(avrukin) Reference to HTML
            @Override public String toHtml(Distribution value) {
                return value.htmlString();
            }
            */
        };
    }

    private static final String BAD_TYPE =
            "Illegal value type for metric: %s; must be one of boolean, int, long, "
                    + "float, double, string, Distribution or some subclass of Enum or "
                    + "com.google.protobuf.Message";

    /**
     * @returns the ValueTypeTraits instance for the specified class.
     * @throws IllegalArgumentException if {@code valueType} was not a legal
     * value type for a metric.
     */
    @SuppressWarnings("unchecked") // For all the typecasts from createXxx to (ValueTypeTraits<V>).
    static <V> ValueTypeTraits<V> getTraits(final Class<V> valueClass) {
        // The unchecked casts are sound because each is dominated by a dynamic
        // test that reveals the value of V---but the compiler doesn't know that.
        if (valueClass == Boolean.class || valueClass == Boolean.TYPE) {
            return (ValueTypeTraits<V>) createBoolean();
        } else if (valueClass == Integer.class || valueClass == Integer.TYPE) {
            return (ValueTypeTraits<V>) createInteger();
        } else if (valueClass == Long.class || valueClass == Long.TYPE) {
            return (ValueTypeTraits<V>) createLong();
        } else if (valueClass == Float.class || valueClass == Float.TYPE) {
            return (ValueTypeTraits<V>) createFloat();
        } else if (valueClass == Double.class || valueClass == Double.TYPE) {
            return (ValueTypeTraits<V>) createDouble();
        } else if (valueClass == String.class) {
            return (ValueTypeTraits<V>) createString();
        } else if (valueClass == Distribution.class) {
            return (ValueTypeTraits<V>) createDistribution(new Distribution(Bucketer.DEFAULT));
        } else if (valueClass == null) {
            throw new NullPointerException("valueClass==null in Metric constructor");
        } else if (Message.class.isAssignableFrom(valueClass)) {
            return (ValueTypeTraits<V>) createMessageTraits((Class<? extends Message>) valueClass);
        } else if (Enum.class.isAssignableFrom(valueClass)) {
            return createEnumTraits(valueClass);
        } else {
            throw new IllegalArgumentException(String.format(BAD_TYPE, valueClass));
        }
    }

    static ValueTypeTraits<Void> getLegacyVoidTrait() {
        return createVoid();
    }

    private static <V extends Message> Descriptor getDescriptorForProto(Class<V> valueClass) {
        Descriptor descriptor;
        try {
            Method getDescriptorMethod = valueClass.getMethod("getDescriptor");
            descriptor = (Descriptor) getDescriptorMethod.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Protocol Message class " + valueClass
                    + " does not have a getDescriptor() method.");
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Could not get descriptor for " + valueClass, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Could not get descriptor for " + valueClass, e);
        }
        return descriptor;
    }

    private static int getMessageTypeId(Descriptor desc) {
        // It's weird that this function doesn't seem to exist in com.google.protobuf.

        // Look for: enum TypeId { MESSAGE_TYPE_ID = 0x12345; }
        for (EnumDescriptor e : desc.getEnumTypes()) {
            Descriptors.EnumValueDescriptor evd;
            if (e.getName().equals("TypeId") && (evd = e.findValueByName("MESSAGE_TYPE_ID")) != null) {
                return evd.getNumber();
            }
        }
        // Look for: extend proto2.bridge.MessageSet { optional M message_set_extension = 0x12345; }
        for (FieldDescriptor ext : desc.getExtensions()) {
            if (ext.getName().equals("message_set_extension")) {  // magic string
                return ext.getNumber();
            }
        }
        throw new IllegalArgumentException("Cannot use Message class " + desc.getFullName()
                + " as a Metric value type since it has neither a TypeId enum nor a MessageSet extension");
    }

    private static <V extends Message> ValueTypeTraits<V> createMessageTraits(
            final Class<V> valueClass) {
        final Descriptor desc = getDescriptorForProto(valueClass);
        final int typeId = getMessageTypeId(desc);
        return new ValueTypeTraits<V>(
                desc.getFullName(),
                EncodedValueType.VALUETYPE_MESSAGE,
                valueClass,
                null) {
            @Override void toStreamValue(StreamValue.Builder streamValue, V value) {
                streamValue.setMessageValue(TypedMessage.newBuilder()
                        .setTypeId(typeId)
                        .setMessage(value.toByteString()));
            }
            @Override void toValueTypeDescriptor(ValueTypeDescriptor.Builder vtd,
                                                 boolean includeMetadata) {
                super.toValueTypeDescriptor(vtd, includeMetadata);
                if (includeMetadata) {
                    vtd.setMessageName(desc.getFullName());
                    if (typeId > 0) {
                        vtd.setMessageTypeId(typeId);
                    }
                }
            }
            @Override public Descriptor getProtoDescriptor() {
                return desc;
            }
        };
    }

    /**
     * Creates traits for an enum type. Enums are a special case of numeric types - they may be
     * stored as longs, but may not be incremented and null is supported.
     */
    private static <V> ValueTypeTraits<V> createEnumTraits(
            final Class<V> valueClass) {
        Preconditions.checkArgument(valueClass.isEnum(), "Expected enum");
        return new ValueTypeTraits<V>(
                valueClass.getName(),
                EncodedValueType.VALUETYPE_ENUM,
                valueClass,
                null) {
            @Override void toStreamValue(StreamValue.Builder streamValue, V value) {
                streamValue.setStringValue(ByteString.copyFromUtf8(value.toString()));
            }

            @Override boolean isConvertableToLong() { return true; }

            @Override void set(AtomicLong holder, V value) {
                holder.set(toLong(value));
            }

            @Override long toLong(V value) {
                return value == null ? -1 : ((Enum) value).ordinal();
            }

            @Override V fromLong(long value) {
                return value == -1 ? null : valueClass.getEnumConstants()[(int) value];
            }
        };
    }

    /**
     * Return the type held in this metric.
     */
    public Class<V> getType() {
        return this.type;
    }
}