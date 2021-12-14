/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;

public final class LogEmitterProvider {

  private final SdkLogEmitterProvider delegate;

  public static LogEmitterProvider from(SdkLogEmitterProvider delegate) {
    return new LogEmitterProvider(delegate);
  }

  private LogEmitterProvider(SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates a {@link LogEmitterBuilder} instance.
   *
   * @param instrumentationName the name of the instrumentation library
   * @return a log emitter builder instance
   */
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    return new SdkLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
  }
}
