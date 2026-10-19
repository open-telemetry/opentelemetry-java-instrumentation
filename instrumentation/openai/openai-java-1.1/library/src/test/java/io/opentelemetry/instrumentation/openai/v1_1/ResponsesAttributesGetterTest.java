/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResponsesAttributesGetterTest {

  private static final ResponseCreateRequest REQUEST =
      ResponseCreateRequest.create(
          ResponseCreateParams.builder().input("hello").model("test-model").build());

  private final ResponsesAttributesGetter getter = new ResponsesAttributesGetter();

  @ParameterizedTest
  @MethodSource("statusCases")
  void mapsStatusToFinishReason(String json, String finishReason) throws JsonProcessingException {
    assertThat(getter.getResponseFinishReasons(REQUEST, parse(json))).containsExactly(finishReason);
  }

  private static Stream<Arguments> statusCases() {
    return Stream.of(
        argumentSet("completed", "{\"status\":\"completed\"}", "stop"),
        argumentSet("failed", "{\"status\":\"failed\"}", "error"),
        argumentSet("cancelled", "{\"status\":\"cancelled\"}", "error"),
        argumentSet(
            "incomplete for output tokens",
            "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}",
            "length"),
        argumentSet(
            "incomplete for content filter",
            "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"content_filter\"}}",
            "content_filter"),
        argumentSet(
            "incomplete without reason",
            "{\"status\":\"incomplete\",\"incomplete_details\":null}",
            "incomplete"));
  }

  @ParameterizedTest
  @MethodSource("outputCases")
  void mapsOutputItemsToFinishReasons(String json, List<String> finishReasons)
      throws JsonProcessingException {
    assertThat(getter.getResponseFinishReasons(REQUEST, parse(json)))
        .containsExactlyElementsOf(finishReasons);
  }

  private static Stream<Arguments> outputCases() {
    String message = "{\"type\":\"message\",\"status\":\"completed\"}";
    String functionCall = "{\"type\":\"function_call\",\"status\":\"completed\"}";
    return Stream.of(
        argumentSet(
            "function call",
            "{\"status\":\"completed\",\"output\":[" + functionCall + "]}",
            singletonList("tool_calls")),
        argumentSet(
            "message and function calls",
            "{\"status\":\"completed\",\"output\":["
                + message
                + ","
                + functionCall
                + ","
                + functionCall
                + "]}",
            asList("stop", "tool_calls")),
        argumentSet(
            "unfinished function call",
            "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"function_call\",\"status\":\"in_progress\"},"
                + message
                + "]}",
            singletonList("stop")),
        argumentSet(
            "incomplete message",
            "{\"status\":\"completed\",\"output\":"
                + "[{\"type\":\"message\",\"status\":\"incomplete\"}]}",
            singletonList("incomplete")),
        argumentSet(
            "function call without response status",
            "{\"output\":[" + functionCall + "]}",
            singletonList("tool_calls")),
        argumentSet(
            "function call in failed response",
            "{\"status\":\"failed\",\"output\":[" + functionCall + "]}",
            singletonList("error")));
  }

  @ParameterizedTest
  @MethodSource("customToolCallCases")
  void mapsCustomToolCallsToFinishReasons(String json, List<String> finishReasons)
      throws JsonProcessingException {
    // custom tool calls were added in SDK 3.x
    assumeTrue(testLatestDeps());
    assertThat(getter.getResponseFinishReasons(REQUEST, parse(json)))
        .containsExactlyElementsOf(finishReasons);
  }

  private static Stream<Arguments> customToolCallCases() {
    return Stream.of(
        argumentSet(
            "custom tool call without status",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"custom_tool_call\"}]}",
            singletonList("tool_calls")),
        argumentSet(
            "custom tool call with null status",
            "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"custom_tool_call\",\"status\":null}]}",
            singletonList("tool_calls")),
        argumentSet(
            "unfinished custom tool call",
            "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"custom_tool_call\",\"status\":\"in_progress\"}]}",
            singletonList("stop")));
  }

  @Test
  void reportsErrorCodeAsErrorType() throws JsonProcessingException {
    Response failed = parse("{\"status\":\"failed\",\"error\":{\"code\":\"server_error\"}}");
    assertThat(getter.getErrorType(REQUEST, failed, null)).isEqualTo("server_error");
    assertThat(getter.getErrorType(REQUEST, parse("{\"status\":\"completed\"}"), null)).isNull();

    ResponseCreateRequest streaming =
        ResponseCreateRequest.createStreaming(
            ResponseCreateParams.builder().input("hello").model("test-model").build());
    streaming.setStreamErrorCode("rate_limit_exceeded");
    assertThat(getter.getErrorType(streaming, null, null)).isEqualTo("rate_limit_exceeded");
  }

  @Test
  void omitsFinishReasonWhileInProgress() throws JsonProcessingException {
    assertThat(getter.getResponseFinishReasons(REQUEST, parse("{\"status\":\"in_progress\"}")))
        .isEmpty();
  }

  @Test
  void extractsDetailedTokenUsage() throws JsonProcessingException {
    Response response =
        parse(
            "{\"usage\":{\"input_tokens\":20,\"input_tokens_details\":{\"cached_tokens\":12},"
                + "\"output_tokens\":10,\"output_tokens_details\":{\"reasoning_tokens\":4},"
                + "\"total_tokens\":30}}");

    assertThat(getter.getUsageInputTokens(REQUEST, response)).isEqualTo(20L);
    assertThat(getter.getUsageCacheReadInputTokens(REQUEST, response)).isEqualTo(12L);
    assertThat(getter.getUsageOutputTokens(REQUEST, response)).isEqualTo(10L);
    assertThat(getter.getUsageReasoningOutputTokens(REQUEST, response)).isEqualTo(4L);
  }

  @Test
  void toleratesMissingFields() throws JsonProcessingException {
    // The typed accessors throw when a required field is missing; the getter must not.
    Response response = parse("{\"usage\":{\"total_tokens\":30}}");

    assertThat(getter.getResponseId(REQUEST, response)).isNull();
    assertThat(getter.getResponseModel(REQUEST, response)).isNull();
    assertThat(getter.getResponseFinishReasons(REQUEST, response)).isEmpty();
    assertThat(getter.getUsageInputTokens(REQUEST, response)).isNull();
    assertThat(getter.getUsageCacheReadInputTokens(REQUEST, response)).isNull();
    assertThat(getter.getUsageOutputTokens(REQUEST, response)).isNull();
    assertThat(getter.getUsageReasoningOutputTokens(REQUEST, response)).isNull();
  }

  private static Response parse(String json) throws JsonProcessingException {
    return ObjectMappers.jsonMapper().readValue(json, Response.class);
  }
}
