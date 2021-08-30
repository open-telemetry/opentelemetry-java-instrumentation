/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.EverythingMatcher;

/** Entrypoint for tracing execution of Quartz jobs. */
public final class QuartzTracing {

  /** Returns a new {@link QuartzTracing} configured with the given {@link OpenTelemetry}. */
  public static QuartzTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link QuartzTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static QuartzTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new QuartzTracingBuilder(openTelemetry);
  }

  QuartzTracing(JobListener jobListener) {
    this.jobListener = jobListener;
  }

  private final JobListener jobListener;

  /** Configures the {@link Scheduler} to enable tracing of jobs. */
  public void configure(Scheduler scheduler) {
    try {
      for (JobListener listener : scheduler.getListenerManager().getJobListeners()) {
        if (listener instanceof TracingJobListener) {
          return;
        }
      }
    } catch (SchedulerException e) {
      // Ignore
    }
    try {
      // We must pass a matcher to work around a bug in Quartz 2.0.0. It's unlikely anyone uses
      // a version before 2.0.2, but it makes muzzle simple.
      scheduler.getListenerManager().addJobListener(jobListener, EverythingMatcher.allJobs());
    } catch (SchedulerException e) {
      throw new IllegalStateException("Could not add JobListener to Scheduler", e);
    }
  }
}
