/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SuppressWarnings("OtelInternalJavadoc")
public class CustomListAppender extends ListAppender<ILoggingEvent> {
  public static boolean lastLogHadTraceId;

  @Override
  protected void append(ILoggingEvent event) {
    // Since list appender just captures the event object it is possible that the trace_id is not
    // present when list appender was called but is added at a later time. Here we record whether
    // trace_id was present in mdc at the time when the event was processed by the list appender.
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13383
    lastLogHadTraceId = event.getMDCPropertyMap().get("trace_id") != null;
    super.append(event);
  }
}
