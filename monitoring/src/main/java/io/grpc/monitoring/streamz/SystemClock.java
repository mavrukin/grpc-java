package io.grpc.monitoring.streamz;

import org.joda.time.Instant;

import javax.inject.Inject;
import java.io.Serializable;

/**
 * Created by avrukin on 1/9/17.
 */
class SystemClock implements Clock, Serializable {
  @Inject
  public SystemClock() {
  }

  @Override
  public Instant now() {
    return new Instant();
  }
}
