package io.grpc.monitoring.streamz;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.Listener;
import com.google.common.util.concurrent.Service.State;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Simple implementation of {@link Listener} that logs {@link Service} state transitions.
 * <p>Example usage:
 * <pre>
 * class MyService extends AbstractScheduledService {
 *   MyService(...) {
 *     ...
 *     addListener(StreamzServiceLoggingListener.get(), MoreExecutors.directExecutor());
 *   }
 * }
 * </pre>
 * <p>
 * It does not log any service specific metadata, but relies on the service to call it from within
 * one of its threads, which were appropriately renamed for easy identification.
 * <p>
 * All but {@link #failed} state transitions are logged with {@link Level#FINE} in order not to
 * pollute production INFO logs.
 * {@link #failed} state transition is logged with {@link Level#WARNING} as it's an indication of
 * an unexpected failure condition.
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
class StreamzServiceLoggingListener extends Listener {
  private static final Listener INSTANCE = new StreamzServiceLoggingListener();
  private final Logger logger = Logger.getLogger(StreamzServiceLoggingListener.class.getName());

  private StreamzServiceLoggingListener() {}

  static Listener get() {
    return INSTANCE;
  }

  @Override
  public void starting() {
    super.starting();
    logger.fine("Service starting...");
  }

  @Override
  public void running() {
    super.running();
    logger.fine("Service started succesfully and running...");
  }

  @Override
  public void stopping(State from) {
    super.stopping(from);
    logger.fine(Utils.formatSafely("Service is stopping (previous state: %s)...", from));
  }

  @Override
  public void terminated(State from) {
    super.terminated(from);
    logger.fine(Utils.formatSafely("Service terminated successfully (previous state: %s).", from));
  }

  @Override
  public void failed(State from, Throwable failure) {
    super.failed(from, failure);
    // This is most likely an unexpected failure -- log as a warning.
    LogRecord logRecord = new LogRecord(Level.WARNING,
        Utils.formatSafely("Service terminated with a failure! Previous state: %s", from));
    logRecord.setThrown(failure);
    logger.log(logRecord);
  }
}