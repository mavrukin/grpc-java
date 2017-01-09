package io.grpc.monitoring.streamz;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import io.grpc.monitoring.streamz.proto.MetricAnnotation;
import io.grpc.monitoring.streamz.proto.MetricDefinition;
import io.grpc.monitoring.streamz.proto.Types.FieldType;
import io.grpc.monitoring.streamz.proto.Types.ValueTypeDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Abstract base class of all Streamz metrics.
 *
 * <p>The Streamz subsystem is initalized automatically when this class
 * is loaded. If the StreamzService is linked in, then the process will
 * announce itself to discovery and export the Streamz rpc service.
 *
 * <p>It encapsulates all the attributes of a metric required by the
 * /streamz HTTP servlet and Streamz RPC service, i.e. facilities
 * for querying its metadata (name, value type, field names, field
 * types, annotations, etc) and its contents (the set of logical
 * cells, their values and field tuples which identify individual cells in
 * multi-dimensional metrics).
 *
 * <p>To instrument code, you probably want {@link MetricFactory}.
 *
 * <p>Subclasses must implement the abstract type() and container()
 * methods to complete the definition.
 *
 * <p>The contents of a metric cannot be mutated via this interface,
 * since, depending on the implementation, they may be read-only,
 * defined by application callbacks, transient, etc.
 *
 * <h2>Concepts</h2>
 *
 * A metric is a named entity exporting time-varying data from the
 * program.  Each metric has a distinct name, which is syntactically
 * like a UNIX path; see {@link #validateMetricName(String)}. Each metric
 * also has a free-form description.
 *
 * <p>A metric may also be annotated by supplying a {@link Metadata}
 * object when it is created.
 *
 * <p>Each metric has a (potentially empty) list of additional fields.
 * "Fieldless" metrics have no dimensions, and thus only a single stream
 * of Values.  "Map" metrics have at least one associated field and
 * thus describe a space of streams. Each such field tuple can be thought of
 * as the key to a value in the metric's map, hence the name.
 *
 * <h2>When to use this class</h2>
 *
 * When writing code that manipulates metrics abstractly (e.g. in
 * the /streamz HTTP servlet and Streamz RPC service code) or when
 * defining new concrete metric implementations.
 *
 * @see MetricFactory
 *
 * <p>GenericMetric is not designed to be subclassed outside the
 * package. There are subtle issues to address before subclassing is safe,
 * such as when the Metric should be added to the namespace.
 *
 * @param <V> the type of each value in the streams (time-series) belonging
 *   to the metric.  V must be one of Boolean, Float, Double, Integer, Long,
 *   String, Enum, {@link Distribution} or a subtype of
 *   {@link com.google.protobuf.Message}. (All of these are immutable except
 *   for Distribution).
 *
 * @param <M> The type of the concrete Metric subclass.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
public abstract class GenericMetric<V, M extends GenericMetric<V, M>> {
    private static final Logger logger = Logger.getLogger(GenericMetric.class.getName());

    private static final long DEFAULT_CELL_RESET_TIMESTAMP_MICROS = Utils.getProcessStartTimeMicros();

    private static final Function<Field<?>, String> GET_FIELD_NAME =
            new Function<Field<?>, String>() {
                @Override
                public String apply(Field<?> field) {
                    return field.getName();
                }
            };

    private final String name;
    private final Metadata metadata;
    private final ValueTypeTraits<V> valueTypeTraits;
    private final List<? extends Field<?>> fields;
    private final List<String> fieldNames;
    private long newCellResetTimestampMicros;

    /**
     * Information on where the metric is defined in source code.
     * These are not maintained as streamz annotations, as they are not stable
     * over time, and not globally unique (as identical metrics may be defined
     * in C++, Java, or other languages).
     */
    private String sourceFilename;
    private int sourceLineNumber;

    GenericMetric(String name, ValueTypeTraits<V> traits, Metadata metadata,
                  ImmutableList<? extends Field<?>> fields) {
      validateMetricName(name);
      this.name = name;
      this.valueTypeTraits = traits;
      this.fields = Preconditions.checkNotNull(fields);
      this.fieldNames = ImmutableList.copyOf(Iterables.transform(this.fields, GET_FIELD_NAME));
      validateFieldNames(name, fieldNames);
      initMetricDefinitionFileAndLine();
      this.newCellResetTimestampMicros = DEFAULT_CELL_RESET_TIMESTAMP_MICROS;
      this.metadata = addFieldDescriptionsToMetadata(metadata, fields);
    }

    private static Metadata addFieldDescriptionsToMetadata(
            Metadata metadata,
            ImmutableList<? extends Field<?>> fields) {
        StringBuilder sb = new StringBuilder();
        for (Field<?> field : fields) {
            if (field.getDescription() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(String.format("%s(%s): %s", field.getName(),
                    field.getNativeType().getSimpleName(), field.getDescription()));
            }
        }

        if (sb.length() > 0) {
            return new Metadata(metadata).addToDescription(sb.toString());
        }
        return metadata;
    }

    /**
     * Allows custom roots to set a root specific cell reset timestamp for scenarios where
     * the root lives outside the current process. This method is only intended to
     * be called by {@link Root}. Processes that proxy via custom roots are required to
     * bring down the root and create a new one when they detect a restart of the remote
     * root. No attempt will be made to accommodate {@link #invalidateNewCellResetTimestamp}.
     */
    synchronized void setDefaultCellResetTimestamp(long defaultCellResetTimeMicros) {
        newCellResetTimestampMicros = defaultCellResetTimeMicros;
    }

    /**
     * Returns the reset timestamp which should be used for new cells.
     * If a cell is removed, the subclass can call
     * {@link #invalidateNewCellResetTimestamp()} and a new reset timestamp will be
     * generated on the next call to this method.
     */
    synchronized long getNewCellResetTimestampMicros() {
        if (newCellResetTimestampMicros < 0) {
            newCellResetTimestampMicros = Utils.getCurrentTimeMicros();
        }
        return newCellResetTimestampMicros;
    }

    /**
     * Marks the metric's reset timestamp for new cells as invalid, so a new
     * one will be generated the next time a cell is created.
     */
    synchronized void invalidateNewCellResetTimestamp() {
        newCellResetTimestampMicros = -1;
    }

    private static void validateMetricName(String name) {
        Preconditions.checkArgument(!name.isEmpty(),
            "Illegal metric name: %s; must not be empty", name);
        ValidationUtils.validateUrlLikeMetricName(name);
    }

    /**
     * Checks that the field names are valid for this metric. We don't allow
     * legacy metric names to be used with URL-like field names and vice versa.
     */
    private static void validateFieldNames(String name, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            validateUrlLikeFieldName(fieldName);
        }
    }

    private static void validateUrlLikeFieldName(String name) {
        if (name.startsWith("/")) {
            // A URL-like field name that starts with a slash implicitly has the
            // same service as its metric. To check validity, we can use any
            // well-formed service name, like "service.com".
            ValidationUtils.validateUrlLikeFieldName("service.com" + name);
        } else {
            ValidationUtils.validateUrlLikeFieldName(name);
        }
    }

    abstract void applyToEachCell(Receiver<GenericCell<V>> callback);

    /**
     * Returns the name of this metric.
     */
    public String getName() { return name; }

    /**
     * Returns the number of fields this metric has.
     */
    public int getNumFields() { return fields.size(); }

    /**
     * Returns the (immutable) list of field (name, type) pairs.
     * Each type is one of the classes String, Boolean, Integer.
     */
    public List<? extends Field<?>> getFields() { return fields; }

    /**
     * Returns the ith element of {@link #getFields()}.
     *
     * @pre i &lt; getNumFields()
     */
    public Field<?> getField(int i) { return fields.get(i); }


    /**
     * Returns the (immutable) list of field names.
     */
    public List<String> getFieldNames() { return fieldNames; }

    /**
     * Returns information about the types of values of this metric.
     */
    ValueTypeTraits<V> getValueTypeTraits() { return valueTypeTraits; }

    /**
     * Returns a read-only view of this metric's annotations.
     */
    Map<String, String> getAnnotations() {
        return Collections.unmodifiableMap(metadata.getAnnotations());
    }

    /**
     * Equivalent to {@code getAnnotations().get(field)}.
     */
    String getAnnotation(String field) {
        return getAnnotations().get(field);
    }

    /**
     * Returns the protocol buffer message representing the metric definition.
     * Metadata annotations are included only if {@code includeDeclarationMetadata} is true.
     */
    MetricDefinition getMetricDefinition(boolean includeDeclarationMetadata) {
        MetricDefinition.Builder definition = MetricDefinition.newBuilder();
        definition.setName(getName());
        ValueTypeDescriptor.Builder descriptor = definition.getTypeDescriptorBuilder();
        getValueTypeTraits().toValueTypeDescriptor(descriptor, includeDeclarationMetadata);

        // Include fields in definition.
        for (Field<?> field : getFields()) {
            definition.addFieldName(field.getName());
            Class<?> fieldType = field.getNativeType();
            definition.addFieldType(
                    fieldType == String.class  ? FieldType.FIELDTYPE_STRING
                            : fieldType == Integer.class ? FieldType.FIELDTYPE_INT
                            : fieldType == Boolean.class ? FieldType.FIELDTYPE_BOOL
                            : null); /* unreachable */
        }

        if (includeDeclarationMetadata) {
            // Include annotations in definition.
            for (Map.Entry<String, String> entry : getAnnotations().entrySet()) {
                MetricAnnotation.Builder annotation = definition.addAnnotationBuilder();
                annotation.setName(entry.getKey());
                annotation.setValue(entry.getValue());
            }
        }

        return definition.build();
    }

    /**
     * Returns the metric's metadata.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Sets the default value, which cells will be initialized to when created.
     * This should be be used to set a non-obvious default for built-in types
     * (e.g. {@code Integer.MAX_VALUE}). Provided value can't be null.
     *
     * @return this.  (Allows chained use, e.g.
     *     new Metric().setDefaultValue().annotate(...)).
     */
    public final M setDefaultValue(V value) {
        valueTypeTraits.setDefaultValue(Preconditions.checkNotNull(value));
        return downcastThis();
    }

    V getDefaultValue() {
        return getValueTypeTraits().getDefaultValue();
    }

    @SuppressWarnings("unchecked")
    private M downcastThis() {
        return (M) this;
    }

    @Override
    public String toString() {
        return getNumFields() == 0 ? name : name + getFieldSignature();
    }

    /**
     * Returns a string of the form {string foo, int bar} describing the fields.
     */
    String getFieldSignature() {
        StringBuilder s = new StringBuilder("{");
        String sep = "";
        for (Field<?> field : fields) {
            s.append(sep)
                    .append(field.getNativeType().getSimpleName().toLowerCase())
                    .append(' ')
                    .append(field.getName());
            sep = ", ";
        }
        s.append("}");
        return s.toString();
    }

    /**
     * Sets the source filename and line number, which are used in
     * {@link StreamzServlet}.
     *
     * <p>This should only ever be called from the constructor - it contains
     * stacktrace parsing logic that assumes this.
     */
    private void initMetricDefinitionFileAndLine() {
        // Find the enclosing stack frame that calls the factory method.
        StackTraceElement frame = getInterestingStackTraceElement();
        if (frame != null) {
            String basename = frame.getFileName();
            String cls = frame.getClassName();
            int dotIndex = cls.lastIndexOf('.');
            if (dotIndex >= 0) {
                // TODO(avrukin) Have this reviewed for gRPC / Open Source
                // Unfortunately the JVM can't give us a
                // google3-relative path name, so we have to guess it from basename and
                // classname.  We get it wrong for the "javatests" tree.
                String filename = "java/"
                        + cls.substring(0, dotIndex).replaceAll("\\.", "/") + "/"
                        + basename;
                this.sourceFilename = filename;
                if (frame.getLineNumber() > 0) {
                    this.sourceLineNumber = frame.getLineNumber();
                }
            }
        }
    }

    private static final String PACKAGE = "io.grpc.monitoring.streamz.";
    private static final ImmutableList<String> IGNORED_CLASSES =
            ImmutableList.of(
                    PACKAGE + "GenericMetric",
                    PACKAGE + "MetricFactory");

    private StackTraceElement getInterestingStackTraceElement() {
        // This is always called from the GenericMetric constructor.
        // So we pop through calls from GenericMetric, any constructors in this package
        // (which will be GenericMetric subclass constructors) and Metrics or MetricFactory
        // factory calls. The next frame after that is the one we're interested in.
        // This should work even for metrics defined in this package (so long as
        // they are not defined in the constructor).

        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();

            if ((className.startsWith(PACKAGE) && methodName.equals("<init>"))) {
                continue;
            }
            if (IGNORED_CLASSES.contains(className)) {
                continue;
            }
            return frame;
        }

        return null;
    }

    void setSourceInformation(String sourceFilename, int sourceLinenumber) {
        this.sourceFilename = sourceFilename;
        this.sourceLineNumber = sourceLinenumber;
    }

    String getSourceFilename() {
        return sourceFilename;
    }

    int getSourceLineNumber() {
        return sourceLineNumber;
    }

    void verifyFieldTuple(FieldTuple fieldTuple) {
        Preconditions.checkArgument(fieldTuple.length() == fields.size(),
                "Wrong number of fields. Got %s for %s", fieldTuple, this);
        for (int ii = 0; ii < fields.size(); ii++) {
          Object field = fieldTuple.get(ii);
          Class<?> requiredType = fields.get(ii).getType();
          Preconditions.checkArgument(requiredType.isInstance(field),
              "Field %s not of required type. Got %s for %s", ii, fieldTuple, this);
        }
    }

    /**
     * For internal use, we want to cast the metric to value type Object.
     * This is only used within the package, and only for reading cell values.
     * i.e. we never use this to try and put the wrong value type into a cell.
     */
    @SuppressWarnings("unchecked")
    GenericMetric<Object, ?> castForReading() {
        return (GenericMetric<Object, ?>) this;
    }

    /**
     * Returns the class type of this metric's value.
     */
    public Class<V> getValueType() {
        return valueTypeTraits.getType();
    }
}