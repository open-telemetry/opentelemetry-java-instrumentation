/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

import javax.annotation.Nullable;

public final class SdkLogEmitterProvider implements LogEmitterProvider {

  private static final SdkLogEmitterProvider INSTANCE = new SdkLogEmitterProvider(null);

  @Nullable private final io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate;

  public static SdkLogEmitterProvider from(
      io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    return new SdkLogEmitterProvider(delegate);
  }

  public static SdkLogEmitterProvider noop() {
    return INSTANCE;
  }

  private SdkLogEmitterProvider(
      @Nullable io.opentelemetry.sdk.logs.SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    if (delegate != null) {
      return new SdkLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
    } else {
      return NoopLogEmitterBuilder.INSTANCE;
    }
  }
}
