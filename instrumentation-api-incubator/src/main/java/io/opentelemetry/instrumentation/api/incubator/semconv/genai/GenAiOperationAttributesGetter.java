/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI operation attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiAttributesExtractor} to obtain the various
 * GenAI attributes in a type-generic way. It will also be used by the {@link
 * GenAiSpanNameExtractor} to generate span name in a type-generic way.
 */
public interface GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getOperationName(REQUEST request);

  @Nullable
  String getOperationTarget(REQUEST request);
}
