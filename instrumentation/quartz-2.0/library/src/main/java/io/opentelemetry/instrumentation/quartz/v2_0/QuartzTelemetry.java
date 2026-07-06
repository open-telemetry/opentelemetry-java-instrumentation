/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Matcher;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerListener;
import org.quartz.impl.matchers.EverythingMatcher;

/** Entrypoint for telemetry instrumentation of Quartz jobs. */
public final class QuartzTelemetry {
  private final JobListener jobListener;
  private final Logger eventLogger;

  /** Returns a new {@link QuartzTelemetry} configured with the given {@link OpenTelemetry}. */
  public static QuartzTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link QuartzTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static QuartzTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new QuartzTelemetryBuilder(openTelemetry);
  }

  QuartzTelemetry(JobListener jobListener, Logger eventLogger) {
    this.jobListener = jobListener;
    this.eventLogger = eventLogger;
  }

  /**
   * Configures the {@link Scheduler} to enable tracing of jobs.
   *
   * <p><strong>NOTE:</strong> If there are job listeners already registered on the Scheduler that
   * may throw exceptions, tracing will be broken. It's important to call this as soon as possible
   * to avoid being affected by other bad listeners, or otherwise ensure listeners you register do
   * not throw exceptions.
   */
  public void configure(Scheduler scheduler) {
    addJobListener(scheduler);
    addSchedulerListener(scheduler);
  }

  private void addJobListener(Scheduler scheduler) {
    try {
      for (JobListener listener : scheduler.getListenerManager().getJobListeners()) {
        if (listener instanceof TracingJobListener) {
          return;
        }
      }
    } catch (SchedulerException ignored) {
      // Ignore
    }
    try {
      // We must pass a matcher to work around a bug in Quartz 2.0.0. It's unlikely anyone uses
      // a version before 2.0.2, but it makes muzzle simple.
      @SuppressWarnings({"rawtypes", "unchecked"})
      Matcher<JobKey>[] matchers = new Matcher[] {EverythingMatcher.allJobs()};
      scheduler.getListenerManager().addJobListener(jobListener, matchers);
    } catch (SchedulerException e) {
      throw new IllegalStateException("Could not add JobListener to Scheduler", e);
    }
  }

  private void addSchedulerListener(Scheduler scheduler) {
    try {
      for (SchedulerListener listener : scheduler.getListenerManager().getSchedulerListeners()) {
        if (listener instanceof TracingSchedulerListener) {
          return;
        }
      }
    } catch (SchedulerException ignored) {
      // Ignore
    }
    try {
      // The scheduler name is only available here (at configuration time), so we capture it now
      // and hand it to the listener to use as an event attribute.
      String schedulerName = scheduler.getSchedulerName();
      scheduler
          .getListenerManager()
          .addSchedulerListener(new TracingSchedulerListener(eventLogger, schedulerName));
    } catch (SchedulerException e) {
      throw new IllegalStateException("Could not add SchedulerListener to Scheduler", e);
    }
  }
}
