/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI retrieval attributes (used by {@code retrieval} operations such as
 * vector store searches).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by {@link GenAiRetrievalAttributesExtractor} to obtain
 * retrieval attributes in a type-generic way.
 */
public interface GenAiRetrievalAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  /** Returns the value of {@code gen_ai.data_source.id} (e.g. the vector store identifier). */
  @Nullable
  String getDataSourceId(REQUEST request);

  /** Returns the value of {@code gen_ai.retrieval.query.text}. Sensitive: opt-in only. */
  @Nullable
  String getQueryText(REQUEST request);

  /**
   * Returns the operation target used to build the span name. Defaults to {@link #getDataSourceId}.
   *
   * <p>Per spec, the retrieval span name is {@code retrieval <gen_ai.data_source.id>} when the data
   * source id is available, falling back to {@code retrieval} alone when it is not (handled by
   * {@link GenAiSpanNameExtractor} when the target is {@code null} or empty).
   */
  @Override
  @Nullable
  default String getOperationTarget(REQUEST request) {
    return getDataSourceId(request);
  }
}
