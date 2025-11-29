/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.incubator;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

public final class AgentIncubatingAttributes {

  public static final AttributeKey<String> GEN_AI_AGENT_DESCRIPTION =
      stringKey("gen_ai.agent.description");
  public static final AttributeKey<String> GEN_AI_AGENT_ID = stringKey("gen_ai.agent.id");
  public static final AttributeKey<String> GEN_AI_AGENT_NAME = stringKey("gen_ai.agent.name");
  public static final AttributeKey<String> GEN_AI_DATA_SOURCE_ID =
      stringKey("gen_ai.data_source.id");
}
