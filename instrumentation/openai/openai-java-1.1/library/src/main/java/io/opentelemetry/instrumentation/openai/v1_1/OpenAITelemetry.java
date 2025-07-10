/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting OpenAI clients. */
@SuppressWarnings("IdentifierName") // Want to match library's convention
public class OpenAITelemetry {
  /** Returns a new {@link OpenAITelemetry} configured with the given {@link OpenTelemetry}. */
  public static OpenAITelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link OpenAITelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static OpenAITelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new OpenAITelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter;

  private final boolean captureMessageContent;

  OpenAITelemetry(
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter,
      boolean captureMessageContent) {
    this.chatInstrumenter = chatInstrumenter;
    this.captureMessageContent = captureMessageContent;
  }

  /** Wraps the provided OpenAIClient, enabling telemetry for it. */
  public OpenAIClient wrap(OpenAIClient client) {
    return new InstrumentedOpenAIClient(client, chatInstrumenter, captureMessageContent)
        .createProxy();
  }
}
