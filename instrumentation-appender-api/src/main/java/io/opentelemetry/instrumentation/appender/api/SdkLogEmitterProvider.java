/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

public final class SdkLogEmitterProvider implements LogEmitterProvider {

  private final io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate;

  public static SdkLogEmitterProvider from(
      io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    return new SdkLogEmitterProvider(delegate);
  }

  private SdkLogEmitterProvider(io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    return new SdkLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
  }
}
