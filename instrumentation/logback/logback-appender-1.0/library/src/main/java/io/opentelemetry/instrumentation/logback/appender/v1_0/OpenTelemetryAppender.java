/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProviderHolder;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.instrumentation.sdk.appender.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static final LogEmitterProviderHolder logEmitterProviderHolder =
      new LogEmitterProviderHolder();

  public OpenTelemetryAppender() {}

  @Override
  protected void append(ILoggingEvent event) {
    LoggingEventMapper.INSTANCE.emit(logEmitterProviderHolder.get(), event);
  }

  /**
   * This should be called once as early as possible in your application initialization logic, often
   * in a {@code static} block in your main class. It should only be called once - an attempt to
   * call it a second time will result in an error. If trying to set the {@link
   * SdkLogEmitterProvider} multiple times in tests, use {@link
   * OpenTelemetryAppender#resetSdkLogEmitterProviderForTest()} between them.
   */
  public static void setSdkLogEmitterProvider(SdkLogEmitterProvider sdkLogEmitterProvider) {
    logEmitterProviderHolder.set(DelegatingLogEmitterProvider.from(sdkLogEmitterProvider));
  }

  /**
   * Unsets the global {@link LogEmitterProvider}. This is only meant to be used from tests which
   * need to reconfigure {@link LogEmitterProvider}.
   */
  public static void resetSdkLogEmitterProviderForTest() {
    logEmitterProviderHolder.resetForTest();
  }
}
