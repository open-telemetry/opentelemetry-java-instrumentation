/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;

/** Adds the Quartz scheduler name to job execution spans. */
final class SchedulerNameAttributesExtractor
    implements AttributesExtractor<JobExecutionContext, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, JobExecutionContext job) {
    try {
      attributes.put(
          QuartzExperimentalAttributes.SCHEDULER_NAME, job.getScheduler().getSchedulerName());
    } catch (SchedulerException ignored) {
      // Scheduler name is unavailable; skip the attribute.
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      JobExecutionContext job,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
