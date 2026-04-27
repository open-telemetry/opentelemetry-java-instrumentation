/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * A {@link SpanNameExtractor} for GenAI Tool execution operations.
 *
 * <p>Constructs span names as {@code execute_tool <tool_name>}.
 */
public final class GenAiToolSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      GenAiToolAttributesGetter<REQUEST, ?> getter) {
    return new GenAiToolSpanNameExtractor<>(getter);
  }

  private final GenAiToolAttributesGetter<REQUEST, ?> getter;

  private GenAiToolSpanNameExtractor(GenAiToolAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String toolName = getter.getToolName(request);
    if (toolName == null || toolName.isEmpty()) {
      return GenAiExtendedAttributes.GenAiOperationNameValues.EXECUTE_TOOL;
    }
    return GenAiExtendedAttributes.GenAiOperationNameValues.EXECUTE_TOOL + ' ' + toolName;
  }
}
