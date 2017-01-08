package io.grpc.monitoring.streamz;

import com.google.common.base.Joiner;
import com.google.protobuf.TextFormat;
import io.grpc.monitoring.streamz.proto.RootDescriptor;
import io.grpc.monitoring.streamz.proto.StreamzAnnouncement;
import junit.framework.TestCase;

public class StreamzAnnouncementBuilderTest extends TestCase {
  public void testStreamzRootLabelsInAnnouncement() throws Exception {
    RootDescriptor mockRootDescriptor = RootDescriptor.newBuilder()
        .setName("")
        .setSpatialContainer("someContainer")
        .setSpatialElement("someElement")
        .addLabels(new StreamzRootLabel("string", "value").makeStreamzAnnouncementLabel())
        .addLabels(new StreamzRootLabel("number", 10L).makeStreamzAnnouncementLabel())
        .addLabels(new StreamzRootLabel("bool", true).makeStreamzAnnouncementLabel())
        .build();
    long startTimeMicros = Utils.getProcessStartTimeMicros();
    long nowMicros = Utils.getCurrentTimeMicros();

    String expectedText = Joiner.on("\n").join(
        "hostname: '" +  Utils.getHostname() + "'",
        "rpcserver_port: 1234",
        "root: ''",
        "label {",
        "  name: 'string'",
        "  string_value: 'value'",
        "}",
        "label {",
        "  name: 'number'",
        "  int64_value: 10",
        "}",
        "label {",
        "  name: 'bool'",
        "  bool_value: true",
        "}",
        "birth_timestamp: " + startTimeMicros,
        "announcement_timestamp: " + nowMicros);

    StreamzAnnouncement.Builder expectedBuilder = StreamzAnnouncement.newBuilder();
    TextFormat.merge(expectedText, expectedBuilder);
    StreamzAnnouncement expected = expectedBuilder.build();
    StreamzAnnouncement actual = StreamzAnnouncementBuilder.newBuilder()
        .addRpcServerPort(1234)
        .setRootDescriptor(mockRootDescriptor)
        .setProcessStartTime(startTimeMicros)
        .setCurrentTime(nowMicros)
        .build();
    assertEquals(expected, actual);
  }

  public void testPortlessAnnouncement() throws Exception {
    RootDescriptor mockRootDescriptor = RootDescriptor.newBuilder()
        .setName("")
        .setSpatialContainer("someContainer")
        .setSpatialElement("someElement")
        .build();
    long startTimeMicros = Utils.getProcessStartTimeMicros();
    long nowMicros = Utils.getCurrentTimeMicros();

    String expectedText = Joiner.on("\n").join(
        "hostname: '" +  Utils.getHostname() + "'",
        "root: ''",
        "birth_timestamp: " + startTimeMicros,
        "announcement_timestamp: " + nowMicros);

    StreamzAnnouncement.Builder expectedBuilder = StreamzAnnouncement.newBuilder();
    TextFormat.merge(expectedText, expectedBuilder);
    StreamzAnnouncement expected = expectedBuilder.build();
    StreamzAnnouncement actual = StreamzAnnouncementBuilder.newBuilder()
        .setRootDescriptor(mockRootDescriptor)
        .setProcessStartTime(startTimeMicros)
        .setCurrentTime(nowMicros)
        .build();
    assertEquals(expected, actual);
  }
}