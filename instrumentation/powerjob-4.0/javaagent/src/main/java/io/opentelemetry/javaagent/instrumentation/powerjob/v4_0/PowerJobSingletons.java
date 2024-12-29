/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import tech.powerjob.worker.core.processor.ProcessResult;

public final class PowerJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.powerjob-4.0";

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.powerjob.experimental-span-attributes", false);
  private static final Instrumenter<PowerJobProcessRequest, ProcessResult> INSTRUMENTER = create();

  public static Instrumenter<PowerJobProcessRequest, ProcessResult> instrumenter() {
    return INSTRUMENTER;
  }

  private static Instrumenter<PowerJobProcessRequest, ProcessResult> create() {
    PowerJobCodeAttributesGetter codeAttributesGetter = new PowerJobCodeAttributesGetter();
    SpanNameExtractor<PowerJobProcessRequest> spanNameExtractor =
        CodeSpanNameExtractor.create(codeAttributesGetter);

    InstrumenterBuilder<PowerJobProcessRequest, ProcessResult> builder =
        Instrumenter.<PowerJobProcessRequest, ProcessResult>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setSpanStatusExtractor(
                (spanStatusBuilder, powerJobProcessRequest, response, error) -> {
                  if (response != null && !response.isSuccess()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, powerJobProcessRequest, response, error);
                  }
                });

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "powerjob"));
      builder.addAttributesExtractor(new PowerJobExperimentalAttributeExtractor());
    }

    return builder.buildInstrumenter();
  }

  private PowerJobSingletons() {}
}
