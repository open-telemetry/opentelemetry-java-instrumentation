/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

final class QuartzSpanNameExtractor implements SpanNameExtractor<JobExecutionContext> {
  @Override
  public String extract(JobExecutionContext job) {
    JobKey key = job.getJobDetail().getKey();
    return key.getGroup() + '.' + key.getName();
  }
}
