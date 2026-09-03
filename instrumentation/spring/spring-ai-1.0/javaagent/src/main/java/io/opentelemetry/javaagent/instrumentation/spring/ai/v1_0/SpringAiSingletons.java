/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.internal.GenAiExceptionEventExtractors.setGenAiClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.springframework.ai.chat.model.ChatResponse;

public class SpringAiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final int DEFAULT_MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH = 8192;
  private static final ContextKey<Boolean> SUPPRESS_NESTED_CHAT_MODEL_INSTRUMENTATION =
      ContextKey.named("opentelemetry-spring-ai-suppress-nested-chat-model-instrumentation");

  private static final Instrumenter<SpringAiRequest, ChatResponse> instrumenter;
  private static final Logger eventLogger =
      GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_NAME);
  private static final boolean captureMessageContent =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
          .get("gen_ai")
          .getBoolean("capture_message_content", false);
  private static final boolean captureMessageContentAsSpanAttributes =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "spring_ai")
          .get("capture_message_content_as_span_attributes/development")
          .getBoolean("enabled", false);
  private static final int messageContentSpanAttributeMaxLength =
      Math.max(
          0,
          DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "spring_ai")
              .get("message_content_span_attribute/development")
              .getInt("max_length", DEFAULT_MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH));

  static {
    SpringAiAttributesGetter getter = new SpringAiAttributesGetter();
    InstrumenterBuilder<SpringAiRequest, ChatResponse> builder =
        Instrumenter.<SpringAiRequest, ChatResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(getter))
            .addAttributesExtractor(GenAiAttributesExtractor.create(getter))
            .addOperationMetrics(GenAiClientMetrics.get());
    setGenAiClientExceptionEventExtractor(builder);
    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpringAiRequest, ChatResponse> instrumenter() {
    return instrumenter;
  }

  public static Logger eventLogger() {
    return eventLogger;
  }

  public static boolean captureMessageContent() {
    return captureMessageContent;
  }

  public static boolean captureMessageContentAsSpanAttributes() {
    return captureMessageContentAsSpanAttributes;
  }

  public static int messageContentSpanAttributeMaxLength() {
    return messageContentSpanAttributeMaxLength;
  }

  public static Context suppressNestedChatModelInstrumentation(Context context) {
    return context.with(SUPPRESS_NESTED_CHAT_MODEL_INSTRUMENTATION, true);
  }

  public static boolean shouldSuppressNestedChatModelInstrumentation(Context context) {
    return Boolean.TRUE.equals(context.get(SUPPRESS_NESTED_CHAT_MODEL_INSTRUMENTATION));
  }

  private SpringAiSingletons() {}
}
