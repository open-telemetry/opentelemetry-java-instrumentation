/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.internal.GenAiExceptionEventExtractors.setGenAiClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.springframework.ai.chat.model.ChatResponse;

public final class SpringAiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final int DEFAULT_MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH = 8192;

  private static final Instrumenter<SpringAiRequest, ChatResponse> INSTRUMENTER;
  private static final Logger EVENT_LOGGER =
      GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_NAME);
  private static final boolean CAPTURE_MESSAGE_CONTENT =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
          .get("gen_ai")
          .getBoolean("capture_message_content", false);
  private static final boolean CAPTURE_MESSAGE_CONTENT_AS_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "spring_ai")
          .get("capture_message_content_as_span_attributes/development")
          .getBoolean("enabled", false);
  private static final int MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH =
      (int)
          Math.min(
              Integer.MAX_VALUE,
              Math.max(
                  0,
                  DeclarativeConfigUtil.getInstrumentationConfig(
                          GlobalOpenTelemetry.get(), "spring_ai")
                      .get("message_content_span_attribute/development")
                      .getLong("max_length", DEFAULT_MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)));

  static {
    SpringAiAttributesGetter getter = new SpringAiAttributesGetter();
    InstrumenterBuilder<SpringAiRequest, ChatResponse> builder =
        Instrumenter.<SpringAiRequest, ChatResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, SpringAiSpanNameExtractor::name)
            .addAttributesExtractor(GenAiAttributesExtractor.create(getter))
            .addOperationMetrics(GenAiClientMetrics.get());
    setGenAiClientExceptionEventExtractor(builder);
    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpringAiRequest, ChatResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static Logger eventLogger() {
    return EVENT_LOGGER;
  }

  public static boolean captureMessageContent() {
    return CAPTURE_MESSAGE_CONTENT;
  }

  public static boolean captureMessageContentAsSpanAttributes() {
    return CAPTURE_MESSAGE_CONTENT_AS_SPAN_ATTRIBUTES;
  }

  public static int messageContentSpanAttributeMaxLength() {
    return MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH;
  }

  private SpringAiSingletons() {}
}
