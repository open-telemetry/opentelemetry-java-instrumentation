/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.step;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.instrumentationName;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.springframework.batch.core.StepExecution;

public class StepSingletons {

  private static final Instrumenter<StepExecution, Void> INSTRUMENTER =
      Instrumenter.<StepExecution, Void>newBuilder(
              GlobalOpenTelemetry.get(), instrumentationName(), StepSingletons::spanName)
          .newInstrumenter();

  public static Instrumenter<StepExecution, Void> stepInstrumenter() {
    return INSTRUMENTER;
  }

  private static String spanName(StepExecution stepExecution) {
    String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
    String stepName = stepExecution.getStepName();
    return "BatchJob " + jobName + "." + stepName;
  }

  private StepSingletons() {}
}
