/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CREATE_AGENT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EMBEDDINGS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EXECUTE_TOOL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenAiSpanNameExtractorTest {

  @Mock GenAiOperationAttributesGetter<Request, Void> getter;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  GenAiAgentAttributesGetter<Request, Void> agentGetter;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  GenAiToolAttributesGetter<Request, Void> toolGetter;

  @ParameterizedTest
  @MethodSource("spanNameParams")
  void shouldExtractSpanName(
      String operationName, String operationTarget, String expectedSpanName) {
    Request request = new Request();
    when(getter.getOperationName(request)).thenReturn(operationName);
    when(getter.getOperationTarget(request)).thenReturn(operationTarget);

    SpanNameExtractor<Request> underTest = GenAiSpanNameExtractor.create(getter);

    assertThat(underTest.extract(request)).isEqualTo(expectedSpanName);
  }

  static Stream<Arguments> spanNameParams() {
    // "retrieval" operation constant is not in semconv-java 1.37.0-alpha; use the literal.
    return Stream.of(
        Arguments.of(CHAT, "gpt-4o", "chat gpt-4o"),
        Arguments.of(EMBEDDINGS, "text-embeddings-v2", "embeddings text-embeddings-v2"),
        Arguments.of(EXECUTE_TOOL, "get_weather", "execute_tool get_weather"),
        Arguments.of(CREATE_AGENT, "summary_agent", "create_agent summary_agent"),
        Arguments.of(INVOKE_AGENT, "order_assistant", "invoke_agent order_assistant"),
        Arguments.of("retrieval", "products-index", "retrieval products-index"),
        Arguments.of("retrieval", null, "retrieval"),
        Arguments.of(CHAT, null, "chat"),
        Arguments.of(CHAT, "", "chat"));
  }

  @Test
  void agentGetterDefaultsOperationTargetToAgentName() {
    Request request = new Request();
    when(agentGetter.getOperationName(request)).thenReturn(INVOKE_AGENT);
    when(agentGetter.getAgentName(request)).thenReturn("order_assistant");

    SpanNameExtractor<Request> underTest = GenAiSpanNameExtractor.create(agentGetter);

    assertThat(underTest.extract(request)).isEqualTo("invoke_agent order_assistant");
  }

  @Test
  void toolGetterDefaultsOperationTargetToToolName() {
    Request request = new Request();
    when(toolGetter.getOperationName(request)).thenReturn(EXECUTE_TOOL);
    when(toolGetter.getToolName(request)).thenReturn("get_weather");

    SpanNameExtractor<Request> underTest = GenAiSpanNameExtractor.create(toolGetter);

    assertThat(underTest.extract(request)).isEqualTo("execute_tool get_weather");
  }

  static class Request {}
}
