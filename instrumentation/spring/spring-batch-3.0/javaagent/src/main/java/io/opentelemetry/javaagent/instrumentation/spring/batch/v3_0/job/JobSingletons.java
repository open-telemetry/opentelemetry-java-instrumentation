/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.job;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.SpringBatchInstrumentationConfig.instrumentationName;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import org.springframework.batch.core.JobExecution;

public class JobSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.spring-batch.experimental-span-attributes", false);

  private static final Instrumenter<JobExecution, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<JobExecution, Void> instrumenter =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), instrumentationName(), JobSingletons::extractSpanName);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenter.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "spring_batch"));
    }
    INSTRUMENTER = instrumenter.buildInstrumenter();
  }

  private static String extractSpanName(JobExecution jobExecution) {
    return "BatchJob " + jobExecution.getJobInstance().getJobName();
  }

  public static Instrumenter<JobExecution, Void> jobInstrumenter() {
    return INSTRUMENTER;
  }

  private JobSingletons() {}
}
