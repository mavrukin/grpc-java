package io.grpc.monitoring.streamz;

import com.google.common.base.Preconditions;
import io.grpc.monitoring.streamz.proto.StreamzAnnouncement.Label;
import java.util.Arrays;

/**
 * Describes a label that should be announced to Discovery.
 *
 * @author ecurran@google.com (Eoin Curran)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class StreamzRootLabel {
  private final String name;
  private final Object value;

  public StreamzRootLabel(String name, String stringValue) {
    this.name = Preconditions.checkNotNull(name);
    this.value = Preconditions.checkNotNull(stringValue);
  }

  public StreamzRootLabel(String name, Boolean boolValue) {
    this.name = Preconditions.checkNotNull(name);
    this.value = Preconditions.checkNotNull(boolValue);
  }

  public StreamzRootLabel(String name, Long int64Value) {
    this.name = Preconditions.checkNotNull(name);
    this.value = Preconditions.checkNotNull(int64Value);
  }

  @Override
  public String toString() {
    return name + "=" + value.toString();
  }

  public String getName() {
    return name;
  }

  public Object getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[]{name, value});
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof StreamzRootLabel)) {
      return false;
    }

    StreamzRootLabel that = (StreamzRootLabel) o;

    return name.equals(that.name) && value.equals(that.value);
  }

  /**
   * Creates a StreamzAnnouncement.Label to describe this StreamzRootLabel.
   */
  public final Label makeStreamzAnnouncementLabel() {
    Label.Builder labelBuilder = Label.newBuilder()
        .setName(name);

    if (value instanceof Boolean) {
      labelBuilder.setBoolValue((Boolean) value);
    } else if (value instanceof Long) {
      labelBuilder.setInt64Value((Long) value);
    } else if (value instanceof String) {
      labelBuilder.setStringValue((String) value);
    }

    return labelBuilder.build();
  }
}