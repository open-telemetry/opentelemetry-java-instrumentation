/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting OpenAI clients. */
@SuppressWarnings("IdentifierName") // Want to match library's convention
public final class OpenAITelemetry {
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
  private final Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter;

  private final Logger eventLogger;

  private final boolean captureMessageContent;

  OpenAITelemetry(
      Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter,
      Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    this.chatInstrumenter = chatInstrumenter;
    this.embeddingsInstrumenter = embeddingsInstrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  /** Wraps the provided OpenAIClient, enabling telemetry for it. */
  public OpenAIClient wrap(OpenAIClient client) {
    return new InstrumentedOpenAiClient(
            client, chatInstrumenter, embeddingsInstrumenter, eventLogger, captureMessageContent)
        .createProxy();
  }
}
