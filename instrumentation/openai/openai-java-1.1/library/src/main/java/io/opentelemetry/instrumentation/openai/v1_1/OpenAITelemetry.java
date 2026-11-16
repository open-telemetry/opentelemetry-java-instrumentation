/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.Response;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting OpenAI clients. */
@SuppressWarnings("IdentifierName") // Want to match library's convention
public final class OpenAITelemetry {
  private final Instrumenter<ChatCompletionRequest, ChatCompletion> chatInstrumenter;
  private final Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter;
  private final Instrumenter<ResponseCreateRequest, Response> responsesInstrumenter;
  private final Logger eventLogger;
  private final boolean captureMessageContent;

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

  OpenAITelemetry(
      Instrumenter<ChatCompletionRequest, ChatCompletion> chatInstrumenter,
      Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter,
      Instrumenter<ResponseCreateRequest, Response> responsesInstrumenter,
      Logger eventLogger,
      boolean captureMessageContent) {
    this.chatInstrumenter = chatInstrumenter;
    this.embeddingsInstrumenter = embeddingsInstrumenter;
    this.responsesInstrumenter = responsesInstrumenter;
    this.eventLogger = eventLogger;
    this.captureMessageContent = captureMessageContent;
  }

  /** Returns an instrumented {@link OpenAIClient} based on the provided client. */
  public OpenAIClient instrument(OpenAIClient client) {
    return new InstrumentedOpenAiClient(
            client,
            chatInstrumenter,
            embeddingsInstrumenter,
            responsesInstrumenter,
            eventLogger,
            captureMessageContent)
        .createProxy();
  }

  /** Returns an instrumented {@link OpenAIClientAsync} based on the provided client. */
  public OpenAIClientAsync instrument(OpenAIClientAsync client) {
    return new InstrumentedOpenAiClientAsync(
            client,
            chatInstrumenter,
            embeddingsInstrumenter,
            responsesInstrumenter,
            eventLogger,
            captureMessageContent)
        .createProxy();
  }
}
