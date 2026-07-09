/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

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

  private final Logger eventLogger;
  private final String schedulerName;
  private final boolean captureExperimentalAttributes;

  TracingSchedulerListener(
      Logger eventLogger, String schedulerName, boolean captureExperimentalAttributes) {
    this.eventLogger = eventLogger;
    this.schedulerName = schedulerName;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
  }

  @Override
  public void schedulerError(String msg, SchedulerException cause) {
    Context parentContext = Context.current();

    if (Span.fromContext(parentContext).getSpanContext().isValid()) {
      return;
    }

    LogRecordBuilder logRecordBuilder =
        eventLogger
            .logRecordBuilder()
            .setEventName("quartz.scheduler.exception")
            .setContext(parentContext)
            .setSeverity(Severity.ERROR)
            .setException(cause);

    if (msg != null) {
      logRecordBuilder.setBody(msg);
    }
    if (captureExperimentalAttributes && schedulerName != null) {
      logRecordBuilder.setAttribute(QuartzExperimentalAttributes.SCHEDULER_NAME, schedulerName);
    }
    logRecordBuilder.emit();
  }
}
