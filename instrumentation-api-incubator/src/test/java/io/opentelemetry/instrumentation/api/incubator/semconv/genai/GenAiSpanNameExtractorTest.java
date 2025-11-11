/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CREATE_AGENT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EMBEDDINGS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EXECUTE_TOOL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.GENERATE_CONTENT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenAiSpanNameExtractorTest {

  @Mock GenAiOperationAttributesGetter<Request, Void> getter;

  @ParameterizedTest
  @MethodSource("spanNameParams")
  void shouldExtractSpanName(
      String operationName, String operationTarget, String expectedSpanName) {
    // given
    Request request = new Request();

    when(getter.getOperationName(request)).thenReturn(operationName);
    when(getter.getOperationTarget(request)).thenReturn(operationTarget);

    SpanNameExtractor<Request> underTest = GenAiSpanNameExtractor.create(getter);

    // when
    String spanName = underTest.extract(request);

    // then
    assertEquals(expectedSpanName, spanName);
  }

  static Stream<Arguments> spanNameParams() {
    return Stream.of(
        Arguments.of(CHAT, "gpt-4o", "chat gpt-4o"),
        Arguments.of(GENERATE_CONTENT, "qwen-max", "generate_content qwen-max"),
        Arguments.of(EMBEDDINGS, "text-embeddings-v2", "embeddings text-embeddings-v2"),
        Arguments.of(EXECUTE_TOOL, "get_weather", "execute_tool get_weather"),
        Arguments.of(CREATE_AGENT, "summary_agent", "create_agent summary_agent"),
        Arguments.of(INVOKE_AGENT, "order_assistant", "invoke_agent order_assistant"));
  }

  static class Request {}
}
