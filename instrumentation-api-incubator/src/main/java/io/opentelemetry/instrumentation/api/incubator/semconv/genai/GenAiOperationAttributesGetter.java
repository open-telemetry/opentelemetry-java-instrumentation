/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI operation attributes common to all GenAI span types.
 *
 * <p>Instrumentation authors will create implementations of this interface (or its sub-interfaces)
 * for their specific library/framework. It will be used by the {@link GenAiSpanNameExtractor} and
 * various GenAI attributes extractors to obtain attributes in a type-generic way.
 */
public interface GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getOperationName(REQUEST request);

  @Nullable
  String getOperationTarget(REQUEST request);
}
