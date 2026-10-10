/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.internal.GenAiExceptionEventExtractors.setGenAiClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

class SpringAiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final Instrumenter<SpringAiRequest, SpringAiResponse> instrumenter;
  private static final Logger eventLogger =
      GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_NAME);
  private static final boolean captureMessageContent =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
          .get("gen_ai")
          .getBoolean("capture_message_content", false);

  static {
    SpringAiAttributesGetter getter = new SpringAiAttributesGetter();
    InstrumenterBuilder<SpringAiRequest, SpringAiResponse> builder =
        Instrumenter.<SpringAiRequest, SpringAiResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(getter))
            .addAttributesExtractor(GenAiAttributesExtractor.create(getter))
            .addOperationMetrics(GenAiClientMetrics.get());
    setGenAiClientExceptionEventExtractor(builder);
    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<SpringAiRequest, SpringAiResponse> instrumenter() {
    return instrumenter;
  }

  static Logger eventLogger() {
    return eventLogger;
  }

  static boolean captureMessageContent() {
    return captureMessageContent;
  }

  private SpringAiSingletons() {}
}
