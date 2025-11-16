/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI tool attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiToolAttributesExtractor} to obtain the
 * various GenAI tool attributes in a type-generic way.
 */
public interface GenAiToolAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getToolDescription(REQUEST request);

  String getToolName(REQUEST request);

  String getToolType(REQUEST request);

  @Nullable
  String getToolCallId(REQUEST request, @Nullable RESPONSE response);
}
