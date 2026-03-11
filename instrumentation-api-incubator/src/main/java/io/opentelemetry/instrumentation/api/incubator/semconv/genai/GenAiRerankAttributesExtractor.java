/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_RERANK_DOCUMENTS_COUNT;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_RERANK_INPUT_DOCUMENTS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_RERANK_OUTPUT_DOCUMENTS;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of GenAI rerank operation attributes.
 *
 * <p>This class delegates to a type-specific {@link GenAiRerankAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class GenAiRerankAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the GenAI Rerank attributes extractor.
   *
   * @param getter the rerank attributes getter
   * @param captureMessageContent whether to capture input/output documents (sensitive data)
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiRerankAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    return new GenAiRerankAttributesExtractor<>(getter, captureMessageContent);
  }

  private final GenAiRerankAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureMessageContent;

  private GenAiRerankAttributesExtractor(
      GenAiRerankAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    this.getter = getter;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(
        GenAiAttributesExtractor.GEN_AI_OPERATION_NAME,
        GenAiExtendedAttributes.GenAiOperationNameValues.RERANK_DOCUMENTS);
    if (SemconvStability.emitGenAiExperimentalConventions()) {
      set(
          attributes,
          GenAiAttributesExtractor.GEN_AI_PROVIDER_NAME,
          getter.getProviderName(request));
    }
    if (SemconvStability.emitOldGenAiSemconv()) {
      set(attributes, GenAiAttributesExtractor.GEN_AI_SYSTEM, getter.getProviderName(request));
    }
    set(attributes, GenAiAttributesExtractor.GEN_AI_REQUEST_MODEL, getter.getRequestModel(request));
    set(attributes, GEN_AI_RERANK_DOCUMENTS_COUNT, getter.getDocumentsCount(request));
    Long topK = getter.getTopK(request);
    if (topK != null) {
      attributes.put(doubleKey("gen_ai.request.top_k"), (double) topK);
    }
    if (captureMessageContent) {
      set(attributes, GEN_AI_RERANK_INPUT_DOCUMENTS, getter.getInputDocuments(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (captureMessageContent) {
      set(attributes, GEN_AI_RERANK_OUTPUT_DOCUMENTS, getter.getOutputDocuments(request, response));
    }
  }

  private static <T> void set(
      AttributesBuilder attributes, AttributeKey<T> key, @Nullable T value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
