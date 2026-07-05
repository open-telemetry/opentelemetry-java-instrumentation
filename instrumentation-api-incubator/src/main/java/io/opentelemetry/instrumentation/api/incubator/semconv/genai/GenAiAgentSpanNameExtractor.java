/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * A {@link SpanNameExtractor} for GenAI agent operations.
 *
 * <p>Constructs span names as {@code <operation_name> <agent_name>}, e.g. {@code invoke_agent
 * weather-agent}. If the agent name is unavailable, the operation name alone is returned.
 */
public final class GenAiAgentSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} for {@code create_agent} operations: {@code create_agent
   * <agent_name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> forCreateAgent(
      GenAiAgentAttributesGetter<REQUEST, ?> getter) {
    return new GenAiAgentSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} for {@code invoke_agent} operations: {@code invoke_agent
   * <agent_name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> forInvokeAgent(
      GenAiAgentAttributesGetter<REQUEST, ?> getter) {
    return new GenAiAgentSpanNameExtractor<>(getter);
  }

  private final GenAiAgentAttributesGetter<REQUEST, ?> getter;

  private GenAiAgentSpanNameExtractor(GenAiAgentAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String operationName = getter.getOperationName(request);
    String agentName = getter.getAgentName(request);
    if (agentName == null || agentName.isEmpty()) {
      return operationName;
    }
    return operationName + ' ' + agentName;
  }
}
