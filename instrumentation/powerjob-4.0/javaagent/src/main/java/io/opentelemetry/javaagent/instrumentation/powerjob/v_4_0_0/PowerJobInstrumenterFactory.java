/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class PowerJobInstrumenterFactory {

  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.powerjob.experimental-span-attributes", false);

  public static Instrumenter<PowerJobProcessRequest, Void> create(String instrumentationName) {
    PowerJobCodeAttributesGetter codeAttributesGetter = new PowerJobCodeAttributesGetter();
    PowerJobSpanNameExtractor spanNameExtractor =
        new PowerJobSpanNameExtractor(codeAttributesGetter);

    InstrumenterBuilder<PowerJobProcessRequest, Void> builder =
        Instrumenter.<PowerJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
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

  private PowerJobInstrumenterFactory() {}
}
