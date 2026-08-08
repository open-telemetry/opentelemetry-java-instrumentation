/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.quartz.SchedulerException;
import org.quartz.listeners.SchedulerListenerSupport;

/**
 * Instruments scheduler-level Quartz events. Extends {@link SchedulerListenerSupport} so we only
 * override the hooks we care about; every other {@code SchedulerListener} method defaults to a
 * no-op and is available as an extension point for future events.
 */
final class TracingSchedulerListener extends SchedulerListenerSupport {

  private final Logger eventLogger;
  @Nullable private final String schedulerName;
  private final boolean captureExperimentalAttributes;

  TracingSchedulerListener(
      Logger eventLogger, @Nullable String schedulerName, boolean captureExperimentalAttributes) {
    this.eventLogger = eventLogger;
    this.schedulerName = schedulerName;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
  }

  @Override
  public void schedulerError(String msg, SchedulerException cause) {
    Context parentContext = Context.current();

    // When a job's execute() method throws an unhandled exception, Quartz reports it both to the
    // job listener and, while the job is still executing, to schedulerError with a message of the
    // form "Job <key> threw an exception.". The job instrumenter already reports that exception
    // using the configured exception signal, so we skip only that specific callback to avoid
    // reporting it twice. Every other scheduler error is emitted, including one raised by another
    // listener while a job happens to be executing, so we don't drop the only signal for those
    // failures. The message suffix is the only field that distinguishes this callback, and it has
    // been stable across Quartz versions; if it ever changes we fall back to emitting the duplicate
    // rather than dropping an unrelated error.
    if (parentContext.get(TracingJobListener.JOB_CONTEXT_KEY) != null
        && msg != null
        && msg.endsWith(" threw an exception.")) {
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
    if (captureExperimentalAttributes) {
      logRecordBuilder.setAttribute(QuartzExperimentalAttributes.SCHEDULER_NAME, schedulerName);
    }
    logRecordBuilder.emit();
  }
}
