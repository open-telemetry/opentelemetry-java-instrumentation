/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.quartz.SchedulerException;
import org.quartz.listeners.SchedulerListenerSupport;

/**
 * Instruments scheduler-level Quartz events. Extends {@link SchedulerListenerSupport} so we only
 * override the hooks we care about; every other {@code SchedulerListener} method defaults to a
 * no-op and is available as an extension point for future events.
 */
final class TracingSchedulerListener extends SchedulerListenerSupport {

  private final Instrumenter<SchedulerError, Void> instrumenter;
  private final String schedulerName;

  TracingSchedulerListener(Instrumenter<SchedulerError, Void> instrumenter, String schedulerName) {
    this.instrumenter = instrumenter;
    this.schedulerName = schedulerName;
  }

  @Override
  public void schedulerError(String msg, SchedulerException cause) {
    Context parentContext = Context.current();

    // Quartz also reports job execution failures through schedulerError, on the same thread while
    // the job execution span is still current. That failure is already recorded on the job span, so
    // emitting another span here would just duplicate it. Only instrument genuine scheduler-level
    // errors, i.e. when no span is currently active.
    if (Span.fromContext(parentContext).getSpanContext().isValid()) {
      return;
    }

    SchedulerError request = new SchedulerError(schedulerName, msg);
    if (!instrumenter.shouldStart(parentContext, request)) {
      return;
    }

    // schedulerError is a point-in-time event with no start/end bracket around work, so we start
    // and immediately end a span that records the failure. The span has no children but surfaces
    // the scheduler error (and the SchedulerException) in traces.
    Context context = instrumenter.start(parentContext, request);
    instrumenter.end(context, request, null, cause);
  }
}
