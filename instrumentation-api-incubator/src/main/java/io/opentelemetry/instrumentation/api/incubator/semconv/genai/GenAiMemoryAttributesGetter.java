/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI memory operation attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiMemoryAttributesExtractor} to obtain memory
 * attributes in a type-generic way.
 */
public interface GenAiMemoryAttributesGetter<REQUEST, RESPONSE> {

  String getMemoryOperation(REQUEST request);

  @Nullable
  String getUserId(REQUEST request);

  @Nullable
  String getAgentId(REQUEST request);

  @Nullable
  String getRunId(REQUEST request);

  @Nullable
  String getMemoryId(REQUEST request);

  @Nullable
  String getMemoryType(REQUEST request);

  @Nullable
  Long getTopK(REQUEST request);

  @Nullable
  Double getThreshold(REQUEST request);

  @Nullable
  Boolean getRerank(REQUEST request);

  /** Sensitive data — only capture when content capturing is enabled. */
  @Nullable
  String getInputMessages(REQUEST request);

  /** Sensitive data — only capture when content capturing is enabled. */
  @Nullable
  String getOutputMessages(REQUEST request, @Nullable RESPONSE response);
}
