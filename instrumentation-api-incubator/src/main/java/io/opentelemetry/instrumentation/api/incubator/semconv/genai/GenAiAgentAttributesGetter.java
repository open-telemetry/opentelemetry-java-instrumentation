/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI agent attributes (used by {@code create_agent} and {@code
 * invoke_agent} operations).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by {@link GenAiAgentAttributesExtractor} to obtain the various
 * GenAI agent attributes in a type-generic way.
 */
public interface GenAiAgentAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  /** Returns the value of {@code gen_ai.agent.id}. */
  @Nullable
  String getAgentId(REQUEST request);

  /** Returns the value of {@code gen_ai.agent.name}. */
  @Nullable
  String getAgentName(REQUEST request);

  /** Returns the value of {@code gen_ai.agent.description}. */
  @Nullable
  String getAgentDescription(REQUEST request);

  /** Returns the value of {@code gen_ai.agent.version}. */
  @Nullable
  String getAgentVersion(REQUEST request);

  /**
   * Returns the operation target used to build the span name. Defaults to the agent name.
   *
   * <p>Per spec, the agent span name is {@code <operation_name> <gen_ai.agent.name>}.
   */
  @Override
  @Nullable
  default String getOperationTarget(REQUEST request) {
    return getAgentName(request);
  }
}
