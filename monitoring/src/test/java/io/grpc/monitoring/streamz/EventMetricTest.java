package io.grpc.monitoring.streamz;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventMetricTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final MetricFactory factory = new TestMetricFactory();

  @Test
  public void testAnnotate() {
    EventMetric0 m = factory.newEventMetric(
        "test.com/test/eventmetric/annotate", Bucketer.DEFAULT,
        new Metadata("test metric").setForEyesOnly()
          .setDeprecation("old, do not use!"));
        // The line above is L19

    Map<String, String> annotations = TestUtils.getMetric(factory, m.getName()).getAnnotations();
    assertThat(annotations.get(Metadata.DEPRECATION)).isEqualTo("old, do not use!");
    assertThat(TestUtils.getMetric(factory, m.getName()).getSourceLineNumber()).isEqualTo(20);

    // assertThat(TestUtils.getMetric(factory, m.getName()).get)
  }
}