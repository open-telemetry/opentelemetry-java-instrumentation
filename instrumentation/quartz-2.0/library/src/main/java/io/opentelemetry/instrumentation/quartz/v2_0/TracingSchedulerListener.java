/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.quartz.SchedulerException;
import org.quartz.listeners.SchedulerListenerSupport;

/**
 * Instruments scheduler-level Quartz events. Extends {@link SchedulerListenerSupport} so we only
 * override the hooks we care about; every other {@code SchedulerListener} method defaults to a
 * no-op and is available as an extension point for future events.
 */
final class TracingSchedulerListener extends SchedulerListenerSupport {

  // Experimental attributes: names/shapes may change until scheduler instrumentation stabilizes.
  private static final AttributeKey<String> SCHEDULER_NAME = stringKey("quartz.scheduler.name");
  private static final AttributeKey<String> ERROR_MESSAGE =
      stringKey("quartz.scheduler.error.message");

  private final Logger eventLogger;
  private final String schedulerName;

  TracingSchedulerListener(Logger eventLogger, String schedulerName) {
    this.eventLogger = eventLogger;
    this.schedulerName = schedulerName;
  }

  @Override
  public void schedulerError(String msg, SchedulerException cause) {
    Context parentContext = Context.current();

    // Quartz also reports job execution failures through schedulerError, on the same thread while
    // the job execution span is still current. That failure is already recorded on the job span, so
    // reporting it again here would just duplicate it. Only report genuine scheduler-level errors,
    // i.e. when no span is currently active.
    if (Span.fromContext(parentContext).getSpanContext().isValid()) {
      return;
    }

    // A scheduler error is a point-in-time occurrence with no duration, so it is emitted as an
    // event (log record) rather than a span.
    LogRecordBuilder logRecordBuilder =
        eventLogger
            .logRecordBuilder()
            .setEventName("quartz.scheduler.error")
            .setContext(parentContext)
            .setSeverity(Severity.ERROR)
            .setAttribute(SCHEDULER_NAME, schedulerName);
    if (msg != null) {
      logRecordBuilder.setAttribute(ERROR_MESSAGE, msg);
    }
    logRecordBuilder.emit();
  }
}
