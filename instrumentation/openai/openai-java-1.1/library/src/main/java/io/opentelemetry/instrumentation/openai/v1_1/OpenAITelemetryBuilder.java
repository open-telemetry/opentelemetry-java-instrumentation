/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.CaptureMessageOptions;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

/** A builder of {@link OpenAITelemetry}. */
@SuppressWarnings("IdentifierName") // Want to match library's convention
public final class OpenAITelemetryBuilder {
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.openai-java-1.1";

  private final OpenTelemetry openTelemetry;

  private boolean captureMessageContent;

  // TODO(cirilla-zmh): Java Instrumentation is still not support structural attributes for
  //  'gen_ai.input.messages'. Implement this once it's supported.
  private boolean emitExperimentalConventions;

  OpenAITelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether emitted log events include full content of user and assistant messages.
   *
   * <p>Note that full content can have data privacy and size concerns and care should be taken when
   * enabling this.
   */
  @CanIgnoreReturnValue
  public OpenAITelemetryBuilder setCaptureMessageContent(boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
    return this;
  }

  /** Sets whether emitted the latest experimental version of GenAI conventions. */
  @CanIgnoreReturnValue
  public OpenAITelemetryBuilder setEmitExperimentalConventions(
      boolean emitExperimentalConventions) {
    this.emitExperimentalConventions = emitExperimentalConventions;
    return this;
  }

  /**
   * Returns a new {@link OpenAITelemetry} with the settings of this {@link OpenAITelemetryBuilder}.
   */
  public OpenAITelemetry build() {
    Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter =
        Instrumenter.<ChatCompletionCreateParams, ChatCompletion>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(ChatAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiAttributesExtractor.create(ChatAttributesGetter.INSTANCE))
            .addOperationMetrics(GenAiClientMetrics.get())
            .buildInstrumenter();

    Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter =
        Instrumenter.<EmbeddingCreateParams, CreateEmbeddingResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(EmbeddingAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                GenAiAttributesExtractor.create(EmbeddingAttributesGetter.INSTANCE))
            .addOperationMetrics(GenAiClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    Logger eventLogger = openTelemetry.getLogsBridge().get(INSTRUMENTATION_NAME);
    return new OpenAITelemetry(
        chatInstrumenter,
        embeddingsInstrumenter,
        eventLogger,
        CaptureMessageOptions.create(captureMessageContent, emitExperimentalConventions));
  }
}
