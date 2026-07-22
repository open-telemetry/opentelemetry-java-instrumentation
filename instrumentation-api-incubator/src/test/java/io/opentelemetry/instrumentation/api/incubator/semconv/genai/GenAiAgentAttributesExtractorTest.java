/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class GenAiAgentAttributesExtractorTest {

  // GenAiIncubatingAttributes is deprecated in semconv 1.42 (GenAI conventions moved to the
  // semantic-conventions-genai repo), so the expected keys are declared inline here.
  private static final AttributeKey<String> GEN_AI_OPERATION_NAME =
      stringKey("gen_ai.operation.name");
  private static final AttributeKey<String> GEN_AI_AGENT_ID = stringKey("gen_ai.agent.id");
  private static final AttributeKey<String> GEN_AI_AGENT_NAME = stringKey("gen_ai.agent.name");
  private static final AttributeKey<String> GEN_AI_AGENT_DESCRIPTION =
      stringKey("gen_ai.agent.description");
  private static final AttributeKey<String> GEN_AI_AGENT_VERSION =
      stringKey("gen_ai.agent.version");

  @Test
  void shouldExtractAllAgentAttributes() {
    AgentRequest request =
        new AgentRequest(
            INVOKE_AGENT, "agent-123", "weather-agent", "Provides weather forecasts", "1.0.0");

    AttributesExtractor<AgentRequest, Void> extractor =
        GenAiAgentAttributesExtractor.create(new TestAgentGetter());

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);
    AttributesBuilder endBuilder = Attributes.builder();
    extractor.onEnd(endBuilder, Context.root(), request, null, null);

    Attributes expected =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "invoke_agent")
            .put(GEN_AI_AGENT_ID, "agent-123")
            .put(GEN_AI_AGENT_NAME, "weather-agent")
            .put(GEN_AI_AGENT_DESCRIPTION, "Provides weather forecasts")
            .put(GEN_AI_AGENT_VERSION, "1.0.0")
            .build();
    assertThat(startBuilder.build()).isEqualTo(expected);
    assertThat(endBuilder.build()).isEqualTo(Attributes.empty());
  }

  @Test
  void shouldOmitNullAgentAttributes() {
    AgentRequest request = new AgentRequest(INVOKE_AGENT, null, null, null, null);

    AttributesExtractor<AgentRequest, Void> extractor =
        GenAiAgentAttributesExtractor.create(new TestAgentGetter());

    AttributesBuilder builder = Attributes.builder();
    extractor.onStart(builder, Context.root(), request);

    assertThat(builder.build())
        .isEqualTo(Attributes.builder().put(GEN_AI_OPERATION_NAME, "invoke_agent").build());
  }

  static class AgentRequest {
    final String operationName;
    @Nullable final String id;
    @Nullable final String name;
    @Nullable final String description;
    @Nullable final String version;

    AgentRequest(
        String operationName,
        @Nullable String id,
        @Nullable String name,
        @Nullable String description,
        @Nullable String version) {
      this.operationName = operationName;
      this.id = id;
      this.name = name;
      this.description = description;
      this.version = version;
    }
  }

  static class TestAgentGetter implements GenAiAgentAttributesGetter<AgentRequest, Void> {
    @Override
    public String getOperationName(AgentRequest request) {
      return request.operationName;
    }

    @Nullable
    @Override
    public String getAgentId(AgentRequest request) {
      return request.id;
    }

    @Nullable
    @Override
    public String getAgentName(AgentRequest request) {
      return request.name;
    }

    @Nullable
    @Override
    public String getAgentDescription(AgentRequest request) {
      return request.description;
    }

    @Nullable
    @Override
    public String getAgentVersion(AgentRequest request) {
      return request.version;
    }
  }
}
