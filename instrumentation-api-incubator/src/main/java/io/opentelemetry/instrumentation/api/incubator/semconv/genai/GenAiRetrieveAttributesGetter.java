/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI document retrieval attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiRetrieveAttributesExtractor} to obtain
 * retrieval attributes in a type-generic way.
 */
public interface GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String getQuery(REQUEST request);

  /** Sensitive data — only capture when content capturing is enabled. */
  @Nullable
  String getDocuments(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String getServerAddress(REQUEST request);

  @Nullable
  Integer getServerPort(REQUEST request);
}
