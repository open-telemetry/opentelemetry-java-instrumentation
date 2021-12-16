/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import javax.annotation.Nullable;

public final class LogEmitterProvider {

  private static final LogEmitterProvider INSTANCE = new LogEmitterProvider(null);

  @Nullable private final SdkLogEmitterProvider delegate;

  public static LogEmitterProvider from(SdkLogEmitterProvider delegate) {
    return new LogEmitterProvider(delegate);
  }

  public static LogEmitterProvider noop() {
    return INSTANCE;
  }

  private LogEmitterProvider(@Nullable SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates a {@link LogEmitterBuilder} instance.
   *
   * @param instrumentationName the name of the instrumentation library
   * @return a log emitter builder instance
   */
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    if (delegate != null) {
      return new SdkLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
    } else {
      return NoopLogEmitterBuilder.INSTANCE;
    }
  }
}
