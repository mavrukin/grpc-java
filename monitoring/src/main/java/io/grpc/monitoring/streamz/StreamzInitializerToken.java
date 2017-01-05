package io.grpc.monitoring.streamz;

import com.google.common.annotations.VisibleForTesting;

/**
 * Initialization token to indicate when pieces of Streamz have been initialized.
 *
 * <p>This hasn't been designed for use with production code; it's merely
 * for testing only.
 *
 * @author konigsberg@google.com (Robert Konigsberg)
 * @author avrukin@google.com (Adopted / Moved to gRPC & Open Source)
 */
@VisibleForTesting
class StreamzInitializerToken {
  private static final StreamzInitializerToken MAIN_TOKEN = new StreamzInitializerToken();
  private static final StreamzInitializerToken IMPL_TOKEN = new StreamzInitializerToken();

  private volatile boolean initialized = false;

  private StreamzInitializerToken() {}

  /**
   * Token indicating that Streamz has been initialized.
   */
  public static StreamzInitializerToken getMainToken() {
    return MAIN_TOKEN;
  }

  /**
   * Token indicating that the RPC service has been initialized.
   * It might not indicate that it is running, as services can
   * be taken down.
   */
  public static StreamzInitializerToken getgRpcServiceToken() {
    return IMPL_TOKEN;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void markInitialized() {
    this.initialized = true;
  }
}