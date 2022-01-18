/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

public final class LogEmitterProviderHolder {

  private final AtomicReference<LogEmitterProvider> instance =
      new AtomicReference<>(NoopLogEmitterProvider.INSTANCE);

  @Nullable private volatile Throwable setInstanceCaller;

  /** Returns the registered {@link LogEmitterProvider}. */
  public LogEmitterProvider get() {
    return instance.get();
  }

  /**
   * Sets the {@link LogEmitterProvider} that should be the instance. Future calls to {@link #get()}
   * will return the provided {@link LogEmitterProvider} instance. This should be called once as
   * early as possible in your application initialization logic, often in a {@code static} block in
   * your main class. It should only be called once - an attempt to call it a second time will
   * result in an error. If trying to set the instance {@link LogEmitterProvider} multiple times in
   * tests, use {@link LogEmitterProviderHolder#resetForTest()} between them.
   */
  public void set(LogEmitterProvider logEmitterProvider) {
    boolean changed = instance.compareAndSet(NoopLogEmitterProvider.INSTANCE, logEmitterProvider);
    if (!changed && (logEmitterProvider != NoopLogEmitterProvider.INSTANCE)) {
      throw new IllegalStateException(
          "LogEmitterProviderHolder.set has already been called. LogEmitterProviderHolder.set "
              + "must be called only once before any calls to LogEmitterProviderHolder.get. "
              + "Previous invocation set to cause of this exception.",
          setInstanceCaller);
    }
    setInstanceCaller = new Throwable();
  }

  /**
   * Unsets the {@link LogEmitterProvider}. This is only meant to be used from tests which need to
   * reconfigure {@link LogEmitterProvider}.
   */
  public void resetForTest() {
    instance.set(NoopLogEmitterProvider.INSTANCE);
  }
}
