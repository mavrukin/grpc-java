package io.grpc.monitoring.streamz;

import com.google.inject.ImplementedBy;
import org.joda.time.Instant;

@ImplementedBy(SystemClock.class)
interface Clock {
  Instant now();
}
