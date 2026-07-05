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
 * for their specific library/framework. It will be used by {@link GenAiSpanNameExtractor} and the
 * various GenAI attributes extractors to obtain attributes in a type-generic way.
 */
public interface GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the value of {@code gen_ai.operation.name} (e.g. {@code chat}, {@code invoke_agent}).
   */
  String getOperationName(REQUEST request);

  /**
   * Returns the target of the operation used to construct the span name (after the operation name).
   *
   * <p>For inference operations this is the request model. For execute_tool it is the tool name.
   * For create_agent / invoke_agent it is the agent name. Returning {@code null} causes the span
   * name to fall back to {@code gen_ai.operation.name}.
   */
  @Nullable
  String getOperationTarget(REQUEST request);
}
