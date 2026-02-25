/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiMessagesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiSpanNameExtractor;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

/** Builder for {@link SpringAiOpenaiTelemetry}. */
public final class SpringAiOpenaiTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-openai-1.0";

  private final OpenTelemetry openTelemetry;

  private boolean captureMessageContent;

  private int contentMaxLength;

  private String captureMessageStrategy;

  SpringAiOpenaiTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets whether to capture message content in spans. Defaults to false. */
  @CanIgnoreReturnValue
  public SpringAiOpenaiTelemetryBuilder setCaptureMessageContent(boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
    return this;
  }

  /** Sets the maximum length of message content to capture. Defaults to 8192. */
  @CanIgnoreReturnValue
  public SpringAiOpenaiTelemetryBuilder setContentMaxLength(int contentMaxLength) {
    this.contentMaxLength = contentMaxLength;
    return this;
  }

  /** Sets the strategy to capture message content. Defaults to "span-attributes". */
  @CanIgnoreReturnValue
  public SpringAiOpenaiTelemetryBuilder setCaptureMessageStrategy(String captureMessageStrategy) {
    this.captureMessageStrategy = captureMessageStrategy;
    return this;
  }

  /**
   * Returns a new {@link SpringAiOpenaiTelemetry} with the settings of this {@link
   * SpringAiOpenaiTelemetryBuilder}.
   */
  public SpringAiOpenaiTelemetry build() {
    MessageCaptureOptions messageCaptureOptions =
        MessageCaptureOptions.create(
            captureMessageContent, contentMaxLength, captureMessageStrategy);

    Instrumenter<ChatCompletionRequest, ChatCompletion> chatCompletionInstrumenter =
        Instrumenter.<ChatCompletionRequest, ChatCompletion>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(ChatModelAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                GenAiAttributesExtractor.create(ChatModelAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                GenAiMessagesExtractor.create(
                    ChatModelAttributesGetter.INSTANCE,
                    ChatModelMessagesProvider.create(messageCaptureOptions),
                    messageCaptureOptions,
                    INSTRUMENTATION_NAME))
            .buildInstrumenter();

    return new SpringAiOpenaiTelemetry(chatCompletionInstrumenter, messageCaptureOptions);
  }
}
