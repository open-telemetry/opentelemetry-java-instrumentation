/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/** A {@link SpanNameExtractor} for GenAI requests. */
public final class GenAiSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to GenAI semantic
   * conventions.
   *
   * <ul>
   *   <li>Inference (chat / text_completion / generate_content) - {@code <gen_ai.operation.name>
   *       <gen_ai.request.model>}.
   *   <li>Embeddings - {@code embeddings <gen_ai.request.model>}.
   *   <li>Execute tool - {@code execute_tool <gen_ai.tool.name>}.
   *   <li>Create/Invoke agent - {@code <gen_ai.operation.name> <gen_ai.agent.name>}.
   *   <li>Retrieval - {@code retrieval}.
   * </ul>
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      GenAiOperationAttributesGetter<REQUEST, ?> attributesGetter) {
    return new GenAiSpanNameExtractor<>(attributesGetter);
  }

  private final GenAiOperationAttributesGetter<REQUEST, ?> getter;

  private GenAiSpanNameExtractor(GenAiOperationAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String operation = getter.getOperationName(request);
    String operationTarget = getter.getOperationTarget(request);
    if (operationTarget == null || operationTarget.isEmpty()) {
      return operation;
    }
    return operation + ' ' + operationTarget;
  }
}
