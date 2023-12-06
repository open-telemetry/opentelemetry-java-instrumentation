/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import org.quartz.JobExecutionContext;

final class QuartzCodeAttributesGetter implements CodeAttributesGetter<JobExecutionContext> {

  @Override
  public Class<?> getCodeClass(JobExecutionContext jobExecutionContext) {
    return jobExecutionContext.getJobDetail().getJobClass();
  }

  @Override
  public String getMethodName(JobExecutionContext jobExecutionContext) {
    return "execute";
  }
}
