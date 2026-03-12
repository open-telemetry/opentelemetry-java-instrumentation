/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_RETRIEVAL_DOCUMENTS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_RETRIEVAL_QUERY;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of GenAI document retrieval attributes.
 *
 * <p>This class delegates to a type-specific {@link GenAiRetrieveAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class GenAiRetrieveAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the GenAI Retrieve attributes extractor.
   *
   * @param getter the retrieve attributes getter
   * @param captureMessageContent whether to capture retrieved documents (sensitive data)
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    return new GenAiRetrieveAttributesExtractor<>(getter, captureMessageContent);
  }

  private final GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureMessageContent;

  private GenAiRetrieveAttributesExtractor(
      GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    this.getter = getter;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(
        GenAiAttributesExtractor.GEN_AI_OPERATION_NAME,
        GenAiExtendedAttributes.GenAiOperationNameValues.RETRIEVE_DOCUMENTS);
    attributes.put(GEN_AI_RETRIEVAL_QUERY, getter.getQuery(request));
    attributes.put(SERVER_ADDRESS, getter.getServerAddress(request));
    Integer port = getter.getServerPort(request);
    if (port != null) {
      attributes.put(SERVER_PORT, (long) port);
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
      attributes.put(GEN_AI_RETRIEVAL_DOCUMENTS, getter.getDocuments(request, response));
    }
  }
}
