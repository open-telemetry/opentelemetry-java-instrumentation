/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingCodeAttributesExtractor
    extends CodeAttributesExtractor<Runnable, Void> {

  @Override
  protected Class<?> codeClass(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getTarget().getClass();
    } else {
      return runnable.getClass();
    }
  }

  @Override
  protected String methodName(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getMethod().getName();
    } else {
      return "run";
    }
  }

  @Override
  @Nullable
  protected String filePath(Runnable runnable) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(Runnable runnable) {
    return null;
  }
}
