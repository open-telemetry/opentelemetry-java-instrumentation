/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;

public class OpenTelemetryAppender extends AppenderBase<ILoggingEvent> {

  public OpenTelemetryAppender() {}

  @Override
  protected void append(ILoggingEvent event) {
    LoggingEventMapper.INSTANCE.capture(event);
  }
}
