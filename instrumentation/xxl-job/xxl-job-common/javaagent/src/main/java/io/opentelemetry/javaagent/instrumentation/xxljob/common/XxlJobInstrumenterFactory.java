/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class XxlJobInstrumenterFactory {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.xxl-job.experimental-span-attributes", false);

  public static Instrumenter<XxlJobProcessRequest, Void> create(String instrumentationName) {
    XxlJobCodeAttributesGetter codeAttributesGetter = new XxlJobCodeAttributesGetter();
    XxlJobSpanNameExtractor spanNameExtractor = new XxlJobSpanNameExtractor(codeAttributesGetter);
    InstrumenterBuilder<XxlJobProcessRequest, Void> builder =
        Instrumenter.<XxlJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setSpanStatusExtractor(
                (spanStatusBuilder, xxlJobProcessRequest, response, error) -> {
                  if (error != null || xxlJobProcessRequest.isFailed()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  }
                });
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "xxl-job"));
      builder.addAttributesExtractor(new XxlJobExperimentalAttributeExtractor());
    }
    return builder.buildInstrumenter();
  }

  private XxlJobInstrumenterFactory() {}
}
