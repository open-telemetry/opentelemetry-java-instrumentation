/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

public final class DelegatingLogEmitterProvider implements LogEmitterProvider {

  private final io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate;

  public static DelegatingLogEmitterProvider from(
      io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    return new DelegatingLogEmitterProvider(delegate);
  }

  private DelegatingLogEmitterProvider(io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    return new DelegatingLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
  }
}
