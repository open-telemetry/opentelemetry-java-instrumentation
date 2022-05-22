/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import org.quartz.JobExecutionContext;

public class QuartzCodeAttributesGetter implements CodeAttributesGetter<JobExecutionContext> {

  @Override
  public Class<?> codeClass(JobExecutionContext jobExecutionContext) {
    return jobExecutionContext.getJobDetail().getJobClass();
  }

  @Override
  public String methodName(JobExecutionContext jobExecutionContext) {
    return "execute";
  }
}
