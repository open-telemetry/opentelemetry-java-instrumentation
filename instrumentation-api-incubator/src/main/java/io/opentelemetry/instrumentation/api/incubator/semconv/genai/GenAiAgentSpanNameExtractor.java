/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * A {@link SpanNameExtractor} for GenAI Agent operations.
 *
 * <p>Constructs span names as {@code <operation_name> <agent_name>}, e.g. {@code invoke_agent
 * weather-agent}.
 */
public final class GenAiAgentSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} for invoke_agent operations: {@code invoke_agent
   * <agent_name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> forInvokeAgent(
      GenAiAgentAttributesGetter<REQUEST, ?> getter) {
    return new GenAiAgentSpanNameExtractor<>(
        getter, GenAiExtendedAttributes.GenAiOperationNameValues.INVOKE_AGENT);
  }

  /**
   * Returns a {@link SpanNameExtractor} for create_agent operations: {@code create_agent
   * <agent_name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> forCreateAgent(
      GenAiAgentAttributesGetter<REQUEST, ?> getter) {
    return new GenAiAgentSpanNameExtractor<>(
        getter, GenAiExtendedAttributes.GenAiOperationNameValues.CREATE_AGENT);
  }

  private final GenAiAgentAttributesGetter<REQUEST, ?> getter;
  private final String operationName;

  private GenAiAgentSpanNameExtractor(
      GenAiAgentAttributesGetter<REQUEST, ?> getter, String operationName) {
    this.getter = getter;
    this.operationName = operationName;
  }

  @Override
  public String extract(REQUEST request) {
    String agentName = getter.getName(request);
    if (agentName == null || agentName.isEmpty()) {
      return operationName;
    }
    return operationName + ' ' + agentName;
  }
}
