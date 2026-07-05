/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_CALL_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EXECUTE_TOOL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class GenAiToolAttributesExtractorTest {

  @Test
  void shouldExtractToolAttributesOnStartAndCallIdOnEnd() {
    ToolRequest request =
        new ToolRequest("get_weather", "Look up the current weather", "function");
    ToolResponse response = new ToolResponse("call_abc123");

    AttributesExtractor<ToolRequest, ToolResponse> extractor =
        GenAiToolAttributesExtractor.create(new TestToolGetter());

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);
    AttributesBuilder endBuilder = Attributes.builder();
    extractor.onEnd(endBuilder, Context.root(), request, response, null);

    Attributes expectedStart =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "execute_tool")
            .put(GEN_AI_TOOL_NAME, "get_weather")
            .put(GEN_AI_TOOL_DESCRIPTION, "Look up the current weather")
            .put(GEN_AI_TOOL_TYPE, "function")
            .build();
    assertThat(startBuilder.build()).isEqualTo(expectedStart);
    assertThat(endBuilder.build())
        .isEqualTo(Attributes.builder().put(GEN_AI_TOOL_CALL_ID, "call_abc123").build());
  }

  @Test
  void shouldOmitNullToolAttributes() {
    ToolRequest request = new ToolRequest(null, null, null);

    AttributesExtractor<ToolRequest, ToolResponse> extractor =
        GenAiToolAttributesExtractor.create(new TestToolGetter());

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);
    AttributesBuilder endBuilder = Attributes.builder();
    extractor.onEnd(endBuilder, Context.root(), request, null, null);

    assertThat(startBuilder.build())
        .isEqualTo(Attributes.builder().put(GEN_AI_OPERATION_NAME, "execute_tool").build());
    assertThat(endBuilder.build()).isEqualTo(Attributes.empty());
  }

  static class ToolRequest {
    @Nullable final String name;
    @Nullable final String description;
    @Nullable final String type;

    ToolRequest(@Nullable String name, @Nullable String description, @Nullable String type) {
      this.name = name;
      this.description = description;
      this.type = type;
    }
  }

  static class ToolResponse {
    final String callId;

    ToolResponse(String callId) {
      this.callId = callId;
    }
  }

  static class TestToolGetter implements GenAiToolAttributesGetter<ToolRequest, ToolResponse> {
    @Override
    public String getOperationName(ToolRequest request) {
      return EXECUTE_TOOL;
    }

    @Nullable
    @Override
    public String getToolName(ToolRequest request) {
      return request.name;
    }

    @Nullable
    @Override
    public String getToolDescription(ToolRequest request) {
      return request.description;
    }

    @Nullable
    @Override
    public String getToolType(ToolRequest request) {
      return request.type;
    }

    @Nullable
    @Override
    public String getToolCallId(ToolRequest request, @Nullable ToolResponse response) {
      return response == null ? null : response.callId;
    }
  }
}
