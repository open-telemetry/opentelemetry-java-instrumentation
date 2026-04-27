/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * A {@link SpanNameExtractor} for GenAI memory operations.
 *
 * <p>Constructs span names as {@code memory_operation <operation_type>}, e.g. {@code
 * memory_operation search}.
 */
public final class GenAiMemorySpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      GenAiMemoryAttributesGetter<REQUEST, ?> getter) {
    return new GenAiMemorySpanNameExtractor<>(getter);
  }

  private final GenAiMemoryAttributesGetter<REQUEST, ?> getter;

  private GenAiMemorySpanNameExtractor(GenAiMemoryAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String operation = getter.getMemoryOperation(request);
    if (operation == null || operation.isEmpty()) {
      return GenAiExtendedAttributes.GenAiOperationNameValues.MEMORY_OPERATION;
    }
    return GenAiExtendedAttributes.GenAiOperationNameValues.MEMORY_OPERATION + ' ' + operation;
  }
}
