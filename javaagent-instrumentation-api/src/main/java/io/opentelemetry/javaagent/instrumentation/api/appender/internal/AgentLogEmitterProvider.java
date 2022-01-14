/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.appender.internal;

import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProviderHolder;

public final class AgentLogEmitterProvider {

  private static final LogEmitterProviderHolder delegate = new LogEmitterProviderHolder();

  /** Returns the registered global {@link LogEmitterProvider}. */
  public static LogEmitterProvider get() {
    return delegate.get();
  }

  /**
   * Sets the {@link LogEmitterProvider} that should be used by the agent. Future calls to {@link
   * #get()} will return the provided {@link LogEmitterProvider} instance. It should only be called
   * once - an attempt to call it a second time will result in an error. If trying to set the
   * instance {@link LogEmitterProvider} multiple times in tests, use {@link
   * LogEmitterProviderHolder#resetForTest()} between them.
   */
  public static void set(LogEmitterProvider logEmitterProvider) {
    delegate.set(logEmitterProvider);
  }

  /**
   * Unsets the {@link LogEmitterProvider}. This is only meant to be used from tests which need to
   * reconfigure {@link LogEmitterProvider}.
   */
  public static void resetForTest() {
    delegate.resetForTest();
  }

  private AgentLogEmitterProvider() {}
}
