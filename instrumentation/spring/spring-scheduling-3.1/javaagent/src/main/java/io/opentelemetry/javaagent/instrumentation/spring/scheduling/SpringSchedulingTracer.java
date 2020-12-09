/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;
import io.opentelemetry.instrumentation.api.tracer.Tracer;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingTracer extends BaseInstrumenter {
  private static final SpringSchedulingTracer TRACER = new SpringSchedulingTracer();

  public static SpringSchedulingTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-scheduling";
  }

  public String spanNameOnRun(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return Tracer.spanNameForMethod(scheduledMethodRunnable.getMethod());
    } else {
      return Tracer.spanNameForClass(runnable.getClass()) + "/run";
    }
  }
}
