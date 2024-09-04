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
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class PowerJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.powerjob-4.0";

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.powerjob.experimental-span-attributes", false);
  private static final Instrumenter<PowerJobProcessRequest, Void> INSTRUMENTER = create();

  private static Instrumenter<PowerJobProcessRequest, Void> create() {
    PowerJobCodeAttributesGetter codeAttributesGetter = new PowerJobCodeAttributesGetter();
    SpanNameExtractor<PowerJobProcessRequest> spanNameExtractor =
        CodeSpanNameExtractor.create(codeAttributesGetter);

    InstrumenterBuilder<PowerJobProcessRequest, Void> builder =
        Instrumenter.<PowerJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setSpanStatusExtractor(
                (spanStatusBuilder, powerJobProcessRequest, response, error) -> {
                  if (error != null || powerJobProcessRequest.isFailed()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  }
                });

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "powerjob"));
      builder.addAttributesExtractor(new PowerJobExperimentalAttributeExtractor());
    }

    return builder.buildInstrumenter();
  }

  private static final PowerJobHelper HELPER =
      PowerJobHelper.create(
          INSTRUMENTER,
          processResult -> {
            if (processResult != null) {
              return !processResult.isSuccess();
            }
            return false;
          });

  public static PowerJobHelper helper() {
    return HELPER;
  }

  private PowerJobSingletons() {}
}
