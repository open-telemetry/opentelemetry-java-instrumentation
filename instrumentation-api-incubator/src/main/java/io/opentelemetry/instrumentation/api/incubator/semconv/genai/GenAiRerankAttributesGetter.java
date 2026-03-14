/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI rerank operation attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiRerankAttributesExtractor} to obtain rerank
 * attributes in a type-generic way.
 */
public interface GenAiRerankAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String getProviderName(REQUEST request);

  @Nullable
  String getRequestModel(REQUEST request);

  @Nullable
  Long getTopK(REQUEST request);

  @Nullable
  Long getDocumentsCount(REQUEST request);

  /** Sensitive data — only capture when content capturing is enabled. */
  @Nullable
  String getInputDocuments(REQUEST request);

  /** Sensitive data — only capture when content capturing is enabled. */
  @Nullable
  String getOutputDocuments(REQUEST request, @Nullable RESPONSE response);
}
