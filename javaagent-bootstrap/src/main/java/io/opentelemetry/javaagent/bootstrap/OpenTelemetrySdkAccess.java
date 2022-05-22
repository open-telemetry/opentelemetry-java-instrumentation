/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.TimeUnit;

/**
 * A helper to facilitate accessing OpenTelemetry SDK methods from instrumentation. Because
 * instrumentation runs in the app classloader, they do not have access to our SDK in the agent
 * classloader. So we use this class in the bootstrap classloader to bridge between the two - the
 * agent classloader will register implementations of needed SDK functions that can be called from
 * instrumentation.
 */
public final class OpenTelemetrySdkAccess {

  /**
   * Interface matching {@code io.opentelemetry.sdk.trace.SdkTracerProvider#forceFlush()} to allow
   * holding a reference to it.
   */
  public interface ForceFlusher {
    /** Executes force flush. */
    void run(int timeout, TimeUnit unit);
  }

  private static volatile ForceFlusher forceFlush;

  /** Forces flushing of pending spans. */
  public static void forceFlush(int timeout, TimeUnit unit) {
    forceFlush.run(timeout, unit);
  }

  /**
   * Sets the {@link Runnable} to execute when instrumentation needs to force flush. This is called
   * from the agent classloader to execute the SDK's force flush mechanism. Instrumentation must not
   * call this.
   */
  public static void internalSetForceFlush(ForceFlusher forceFlush) {
    if (OpenTelemetrySdkAccess.forceFlush != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    OpenTelemetrySdkAccess.forceFlush = forceFlush;
  }

  private OpenTelemetrySdkAccess() {}
}
