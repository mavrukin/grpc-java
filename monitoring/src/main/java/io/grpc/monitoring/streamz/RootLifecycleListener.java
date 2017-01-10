package io.grpc.monitoring.streamz;

/**
 * The {@link RootLifecycleListener} is used for notifying the Streamz service of custom
 * {@link Root} registration lifecycle events.
 *
 * @author gnirodi@google.com (Gautam Nirodi)
 */
interface RootLifecycleListener {
    /**
     * Called when a new custom {@link Root} is successfully built.
     */
    void onRegister(Root root);

    /**
     * Called when the a custom {@link Root} is destroyed.
     */
    void onUnregister(Root root);
}