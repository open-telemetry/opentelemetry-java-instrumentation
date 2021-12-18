/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

import javax.annotation.Nullable;

public final class GlobalLogEmitterProvider {

  private static final Object mutex = new Object();

  private static volatile LogEmitterProvider globalLogEmitterProvider = null;

  @Nullable private static Throwable setGlobalCaller;

  /** Returns the registered global {@link LogEmitterProvider}. */
  public static LogEmitterProvider get() {
    LogEmitterProvider logEmitterProvider = globalLogEmitterProvider;
    if (logEmitterProvider == null) {
      synchronized (mutex) {
        logEmitterProvider = globalLogEmitterProvider;
        if (logEmitterProvider == null) {
          set(NoopLogEmitterProvider.INSTANCE);
          return NoopLogEmitterProvider.INSTANCE;
        }
      }
    }
    return logEmitterProvider;
  }

  /**
   * Sets the {@link LogEmitterProvider} that should be the global instance. Future calls to {@link
   * #get()} will return the provided {@link LogEmitterProvider} instance. This should be called
   * once as early as possible in your application initialization logic, often in a {@code static}
   * block in your main class. It should only be called once - an attempt to call it a second time
   * will result in an error. If trying to set the global {@link LogEmitterProvider} multiple times
   * in tests, use {@link GlobalLogEmitterProvider#resetForTest()} between them.
   */
  public static void set(LogEmitterProvider logEmitterProvider) {
    synchronized (mutex) {
      if (globalLogEmitterProvider != null) {
        throw new IllegalStateException(
            "GlobalLogEmitterProvider.set has already been called. GlobalLogEmitterProvider.set "
                + "must be called only once before any calls to GlobalLogEmitterProvider.get. "
                + "Previous invocation set to cause of this exception.",
            setGlobalCaller);
      }
      globalLogEmitterProvider = logEmitterProvider;
      setGlobalCaller = new Throwable();
    }
  }

  /**
   * Unsets the global {@link LogEmitterProvider}. This is only meant to be used from tests which
   * need to reconfigure {@link LogEmitterProvider}.
   */
  public static void resetForTest() {
    globalLogEmitterProvider = null;
  }

  private GlobalLogEmitterProvider() {}
}
