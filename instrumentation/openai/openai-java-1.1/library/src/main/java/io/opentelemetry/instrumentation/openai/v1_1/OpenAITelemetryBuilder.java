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
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;

/** A builder of {@link OpenAITelemetry}. */
@SuppressWarnings("IdentifierName") // Want to match library's convention
public final class OpenAITelemetryBuilder {
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.openai-java-1.1";

  private final OpenTelemetry openTelemetry;

  private boolean captureMessageContent;

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

  /**
   * Returns a new {@link OpenAITelemetry} with the settings of this {@link OpenAITelemetryBuilder}.
   */
  public OpenAITelemetry build() {
    InstrumenterBuilder<ChatCompletionCreateParams, ChatCompletion> chatBuilder =
        Instrumenter.<ChatCompletionCreateParams, ChatCompletion>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(ChatAttributesGetter.INSTANCE))
            .addAttributesExtractor(GenAiAttributesExtractor.create(ChatAttributesGetter.INSTANCE))
            .addOperationMetrics(GenAiClientMetrics.get());
    Experimental.setExceptionEventExtractor(chatBuilder, GenAiExceptionEventExtractors.client());
    Instrumenter<ChatCompletionCreateParams, ChatCompletion> chatInstrumenter =
        chatBuilder.buildInstrumenter();

    InstrumenterBuilder<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsBuilder =
        Instrumenter.<EmbeddingCreateParams, CreateEmbeddingResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                GenAiSpanNameExtractor.create(EmbeddingAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                GenAiAttributesExtractor.create(EmbeddingAttributesGetter.INSTANCE))
            .addOperationMetrics(GenAiClientMetrics.get());
    Experimental.setExceptionEventExtractor(
        embeddingsBuilder, GenAiExceptionEventExtractors.client());
    Instrumenter<EmbeddingCreateParams, CreateEmbeddingResponse> embeddingsInstrumenter =
        embeddingsBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());

    Logger eventLogger = openTelemetry.getLogsBridge().get(INSTRUMENTATION_NAME);
    return new OpenAITelemetry(
        chatInstrumenter, embeddingsInstrumenter, eventLogger, captureMessageContent);
  }
}
