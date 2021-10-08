/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.quartz.JobExecutionContext;

public class QuartzCodeAttributesExtractor
    extends CodeAttributesExtractor<JobExecutionContext, Void> {
  @Override
  protected Class<?> codeClass(JobExecutionContext jobExecutionContext) {
    return jobExecutionContext.getJobDetail().getJobClass();
  }

  @Override
  protected String methodName(JobExecutionContext jobExecutionContext) {
    return "execute";
  }

  @Override
  @Nullable
  protected String filePath(JobExecutionContext jobExecutionContext) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(JobExecutionContext jobExecutionContext) {
    return null;
  }
}
