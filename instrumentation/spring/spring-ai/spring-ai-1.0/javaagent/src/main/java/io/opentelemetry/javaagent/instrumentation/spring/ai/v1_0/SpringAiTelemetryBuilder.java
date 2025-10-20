/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAgentAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiMessagesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.tool.GenAiToolAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client.ChatClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client.ChatClientMessagesProvider;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool.ToolCallAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool.ToolCallRequest;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class SpringAiTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";

  private final OpenTelemetry openTelemetry;
  private boolean captureMessageContent;

  private int contentMaxLength;

  private String captureMessageStrategy;

  SpringAiTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether to capture message content in spans. Defaults to false.
   */
  @CanIgnoreReturnValue
  public SpringAiTelemetryBuilder setCaptureMessageContent(boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
    return this;
  }

  /**
   * Sets the maximum length of message content to capture. Defaults to 8192.
   */
  @CanIgnoreReturnValue
  public SpringAiTelemetryBuilder setContentMaxLength(int contentMaxLength) {
    this.contentMaxLength = contentMaxLength;
    return this;
  }

  /**
   * Sets the strategy to capture message content. Defaults to "span-attributes".
   */
  @CanIgnoreReturnValue
  public SpringAiTelemetryBuilder setCaptureMessageStrategy(String captureMessageStrategy) {
    this.captureMessageStrategy = captureMessageStrategy;
    return this;
  }

  public SpringAiTelemetry build() {
    MessageCaptureOptions messageCaptureOptions = MessageCaptureOptions.create(
        captureMessageContent, contentMaxLength, captureMessageStrategy);

    Instrumenter<ChatClientRequest, ChatClientResponse> chatClientInstrumenter =
        Instrumenter.<ChatClientRequest, ChatClientResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(ChatClientAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiAttributesExtractor.create(ChatClientAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiAgentAttributesExtractor.create(ChatClientAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiMessagesExtractor.create(
                ChatClientAttributesGetter.INSTANCE,
                ChatClientMessagesProvider.create(messageCaptureOptions),
                messageCaptureOptions, INSTRUMENTATION_NAME))
            .buildInstrumenter();

    Instrumenter<ToolCallRequest, String> toolCallInstrumenter =
        Instrumenter.<ToolCallRequest, String>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(ToolCallAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiToolAttributesExtractor.create(
                ToolCallAttributesGetter.INSTANCE, messageCaptureOptions))
            .buildInstrumenter();

    return new SpringAiTelemetry(chatClientInstrumenter, toolCallInstrumenter, messageCaptureOptions);
  }
}
