/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of GenAI retrieval attributes for {@code retrieval} spans.
 *
 * <p>This class delegates to a type-specific {@link GenAiRetrievalAttributesGetter} for individual
 * attribute extraction from request/response objects.
 *
 * <p>The retrieval query text is sensitive and is only emitted when {@code captureMessageContent}
 * is true. {@code gen_ai.provider.name} is not emitted by this extractor; the surrounding
 * instrumenter should attach it separately.
 */
public final class GenAiRetrievalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from GenAiIncubatingAttributes
  private static final AttributeKey<String> GEN_AI_DATA_SOURCE_ID =
      stringKey("gen_ai.data_source.id");
  private static final AttributeKey<String> GEN_AI_RETRIEVAL_QUERY_TEXT =
      stringKey("gen_ai.retrieval.query.text");

  /**
   * Creates a GenAI retrieval attributes extractor that does not capture sensitive query text.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return create(attributesGetter, false);
  }

  /**
   * Creates a GenAI retrieval attributes extractor.
   *
   * @param captureMessageContent whether to capture the retrieval query text (sensitive data)
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      boolean captureMessageContent) {
    return new GenAiRetrievalAttributesExtractor<>(attributesGetter, captureMessageContent);
  }

  private final GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureMessageContent;

  private GenAiRetrievalAttributesExtractor(
      GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    this.getter = getter;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(GEN_AI_OPERATION_NAME, getter.getOperationName(request));
    attributes.put(GEN_AI_DATA_SOURCE_ID, getter.getDataSourceId(request));
    if (captureMessageContent) {
      attributes.put(GEN_AI_RETRIEVAL_QUERY_TEXT, getter.getQueryText(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
