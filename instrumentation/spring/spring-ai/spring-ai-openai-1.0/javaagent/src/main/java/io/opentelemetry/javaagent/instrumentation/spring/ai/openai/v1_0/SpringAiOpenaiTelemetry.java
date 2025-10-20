/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/**
 * Entrypoint for instrumenting Spring AI OpenAI clients.
 */
public final class SpringAiOpenaiTelemetry {

  /**
   * Returns a new {@link SpringAiOpenaiTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static SpringAiOpenaiTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringAiOpenaiTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ChatCompletionRequest, ChatCompletion> chatCompletionInstrumenter;
  private final MessageCaptureOptions messageCaptureOptions;

  SpringAiOpenaiTelemetry(
      Instrumenter<ChatCompletionRequest, ChatCompletion> chatCompletionInstrumenter,
      MessageCaptureOptions messageCaptureOptions) {
    this.chatCompletionInstrumenter = chatCompletionInstrumenter;
    this.messageCaptureOptions = messageCaptureOptions;
  }

  public Instrumenter<ChatCompletionRequest, ChatCompletion> chatCompletionInstrumenter() {
    return chatCompletionInstrumenter;
  }

  public MessageCaptureOptions messageCaptureOptions() {
    return messageCaptureOptions;
  }

}
