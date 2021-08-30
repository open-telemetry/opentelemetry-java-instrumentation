/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.SchedulerException;

final class TracingJobListener implements JobListener {

  private final Instrumenter<JobExecutionContext, Void> instrumenter;

  TracingJobListener(Instrumenter<JobExecutionContext, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public String getName() {
    return TracingJobListener.class.getName();
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {
    // TODO(anuraaga): Consider adding an attribute for vetoed jobs.
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext job) {
    Context parentCtx = Context.current();
    if (!instrumenter.shouldStart(parentCtx, job)) {
      return;
    }

    Context context = instrumenter.start(parentCtx, job);
    job.put(Context.class, context);

    // Listeners are executed synchronously on the same thread starting here.
    // https://github.com/quartz-scheduler/quartz/blob/d42fb7770f287afbf91f6629d90e7698761ad7d8/quartz-core/src/main/java/org/quartz/core/JobRunShell.java#L173
    // However, if a listener before this one throws an exception in wasExecuted, we won't be
    // executed. Library instrumentation users need to make sure we're registered first to prevent
    // scope leaks.
    Scope scope = context.makeCurrent();
    job.put(Scope.class, scope);
  }

  @Override
  public void jobWasExecuted(JobExecutionContext job, JobExecutionException error) {
    Scope scope = (Scope) job.get(Scope.class);
    if (scope != null) {
      scope.close();
    }

    Context context = (Context) job.get(Context.class);
    if (context == null) {
      // Would only happen if we didn't start a span (maybe a previous joblistener threw an
      // exception before ours could process the start event).
      return;
    }

    Throwable userError = error;
    while (userError instanceof SchedulerException) {
      userError = ((SchedulerException) userError).getUnderlyingException();
    }
    instrumenter.end(context, job, null, userError);
  }
}
