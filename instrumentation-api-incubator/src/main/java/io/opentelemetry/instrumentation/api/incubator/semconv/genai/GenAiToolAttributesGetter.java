/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI tool attributes (used by {@code execute_tool} operations).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by {@link GenAiToolAttributesExtractor} to obtain the various
 * GenAI tool attributes in a type-generic way.
 */
public interface GenAiToolAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  /** Returns the value of {@code gen_ai.tool.name}. */
  @Nullable
  String getToolName(REQUEST request);

  /** Returns the value of {@code gen_ai.tool.description}. */
  @Nullable
  String getToolDescription(REQUEST request);

  /** Returns the value of {@code gen_ai.tool.type} (e.g. {@code function}, {@code retrieval}). */
  @Nullable
  String getToolType(REQUEST request);

  /** Returns the value of {@code gen_ai.tool.call.id}. */
  @Nullable
  String getToolCallId(REQUEST request, @Nullable RESPONSE response);

  /**
   * Returns the operation target used to build the span name. Defaults to the tool name.
   *
   * <p>Per spec, the execute_tool span name is {@code execute_tool <gen_ai.tool.name>}.
   */
  @Override
  @Nullable
  default String getOperationTarget(REQUEST request) {
    return getToolName(request);
  }
}
