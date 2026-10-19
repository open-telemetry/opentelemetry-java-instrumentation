/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_REASONING_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.INPUT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.OUTPUT;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.async.ResponseServiceAsync;
import com.openai.services.blocking.ResponseService;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// TODO: Remove after https://github.com/open-telemetry/semantic-conventions-genai/issues/247
// is resolved.
@SuppressWarnings("deprecation")
public abstract class AbstractResponsesTest extends AbstractOpenAiTest {

  private static final String TEST_MODEL = "gpt-4o-mini";
  private static final String TEST_RESPONSE_MODEL = "gpt-4o-mini-2024-07-18";
  private static final String TEST_INPUT =
      "Answer in up to 3 words: Which ocean contains Bouvet Island?";

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void basic() {
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    Response response = doResponses(params);
    assertThat(response.id()).startsWith("resp_");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 4L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(22.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                                point ->
                                    point
                                        .hasSum(4.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, OUTPUT)))));
  }

  @Test
  void allTheClientOptions() {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .input(TEST_INPUT)
            .model(TEST_MODEL)
            .maxOutputTokens(50)
            .temperature(0.5)
            .topP(1.0)
            .build();

    Response response = doResponses(params);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_MAX_TOKENS, 50L),
                                    equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.5),
                                    equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 4L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @Test
  void toolCalls() {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .input("What's the weather in Paris?")
            .model(TEST_MODEL)
            .addTool(
                FunctionTool.builder()
                    .name("get_weather")
                    .parameters(
                        FunctionTool.Parameters.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .putAdditionalProperty(
                                "properties",
                                JsonValue.from(
                                    singletonMap("location", singletonMap("type", "string"))))
                            .putAdditionalProperty(
                                "required", JsonValue.from(singletonList("location")))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build())
                    .strict(true)
                    .build())
            .build();

    Response response = doResponses(params);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("tool_calls")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 57L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 15L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @Test
  void customToolCall() {
    // custom tools were added in SDK 3.x
    assumeTrue(testLatestDeps());
    ResponseCreateParams params =
        testHelper.withCustomTool(
            ResponseCreateParams.builder()
                .input("Count the orders placed today.")
                .model(TEST_MODEL)
                .build(),
            "run_sql");

    Response response = doResponses(params);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("tool_calls")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 48L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 21L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @Test
  void stream() {
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    List<ResponseStreamEvent> events = doResponsesStreaming(params);
    assertThat(events).anySatisfy(event -> assertThat(event.isCompleted()).isTrue());
    String responseId =
        events.stream()
            .filter(ResponseStreamEvent::isCompleted)
            .findFirst()
            .map(event -> event.asCompleted().response().id())
            .orElseThrow(AssertionError::new);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, responseId),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 4L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @Test
  void streamIncomplete() {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .input(TEST_INPUT)
            .model(TEST_MODEL)
            .maxOutputTokens(16)
            .build();

    List<ResponseStreamEvent> events = doResponsesStreaming(params);
    assertThat(events).anySatisfy(event -> assertThat(event.isIncomplete()).isTrue());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasStatus(StatusData.unset())
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_MAX_TOKENS, 16L),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, "resp_14783streamincomplete"),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("length")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 16L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @Test
  void streamFailed() {
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).temperature(0.2).build();

    List<ResponseStreamEvent> events = doResponsesStreaming(params);
    assertThat(events).anySatisfy(event -> assertThat(event.isFailed()).isTrue());

    // The SDK delivers response.failed as an ordinary event, so nothing is thrown; the span is
    // still marked as an error.
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasStatus(StatusData.error())
                                .hasException(null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, "server_error"),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.2),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, "resp_14783streamfailed"),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("error"))))));
  }

  @Test
  void streamError() {
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).temperature(0.4).build();

    List<ResponseStreamEvent> events = doResponsesStreaming(params);
    assertThat(events).anySatisfy(event -> assertThat(event.isError()).isTrue());

    // A typed error event ends the stream without a terminal response or a thrown exception.
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasStatus(StatusData.error())
                                .hasException(null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, "server_error"),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.4),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, "resp_14783streamerror"),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL)))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(ERROR_TYPE, "server_error"),
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL)))));
  }

  @Test
  void streamFailure() {
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).temperature(0.0).build();

    Throwable thrown = catchThrowable(() -> doResponsesStreaming(params));
    assertThat(thrown).isNotNull();

    // The span keeps the id and model from the response.created event that arrived before the
    // malformed one.
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasStatus(StatusData.error())
                                .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, thrown.getClass().getName()),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.0),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, "resp_14783streamfailure"),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL)))));

    // The HTTP exchange itself succeeded, so unlike connectionError there is no transport-level
    // exception log, only the GenAI one.
    if (emitExceptionAsLogs()) {
      getTesting()
          .waitAndAssertLogRecords(
              logRecord ->
                  logRecord
                      .hasSeverity(Severity.WARN)
                      .hasEventName("gen_ai.client.operation.exception")
                      .hasException(thrown)
                      .hasTotalAttributeCount(3));
    }
  }

  @Test
  void connectionError() {
    OpenAIClient client =
        wrap(
            OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    cleanup.deferCleanup(client::close);
    cleanup.deferCleanup(clientAsync::close);

    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    Throwable thrown = catchThrowable(() -> doResponses(params, client, clientAsync));
    assertThat(thrown).isInstanceOf(OpenAIIoException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, OpenAIIoException.class.getName()),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL)))));

    getTesting().waitAndAssertLogRecords(genAiClientExceptionLogs(thrown));
  }

  @Test
  void streamConnectionError() {
    OpenAIClient client =
        wrap(
            OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    cleanup.deferCleanup(client::close);
    cleanup.deferCleanup(clientAsync::close);

    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    Throwable thrown = catchThrowable(() -> doResponsesStreaming(params, client, clientAsync));
    assertThat(thrown).isInstanceOf(OpenAIIoException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, OpenAIIoException.class.getName()),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_STREAM, true)))));

    getTesting().waitAndAssertLogRecords(genAiClientExceptionLogs(thrown));
  }

  private static Stream<Arguments> requestOptionsCases() {
    return Stream.of(
        argumentSet("default request options", false),
        argumentSet("explicit request options", true));
  }

  @ParameterizedTest
  @MethodSource("requestOptionsCases")
  void structured(boolean explicitOptions) {
    // structured-output overloads were added in SDK 2.x
    assumeTrue(testLatestDeps());
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    Response response =
        doResponses(
            (service, p) -> testHelper.createStructured(service, p, explicitOptions),
            (service, p) -> testHelper.createStructured(service, p, explicitOptions),
            params,
            getClient(),
            getClientAsync());
    assertThat(response.id()).startsWith("resp_");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 61L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 8L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @ParameterizedTest
  @MethodSource("requestOptionsCases")
  void structuredStream(boolean explicitOptions) {
    // structured-output overloads were added in SDK 2.x
    assumeTrue(testLatestDeps());
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    List<ResponseStreamEvent> events =
        doResponsesStreaming(
            (service, p) -> testHelper.createStructuredStreaming(service, p, explicitOptions),
            (service, p) -> testHelper.createStructuredStreaming(service, p, explicitOptions),
            params);
    String responseId =
        events.stream()
            .filter(ResponseStreamEvent::isCompleted)
            .findFirst()
            .map(event -> event.asCompleted().response().id())
            .orElseThrow(AssertionError::new);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, responseId),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 61L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 8L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  @ParameterizedTest
  @MethodSource("withOptionsCases")
  void withOptions(
      Function<OpenAIClient, ResponseService> sync,
      Function<OpenAIClientAsync, ResponseServiceAsync> async) {
    // withOptions was added in SDK 2.x
    assumeTrue(testLatestDeps());
    ResponseCreateParams params =
        ResponseCreateParams.builder().input(TEST_INPUT).model(TEST_MODEL).build();

    Response response =
        call(
            client -> sync.apply(client).create(params),
            client -> async.apply(client).create(params));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            assertResponsesSpan(span)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_RESPONSE_ID, response.id()),
                                    equalTo(GEN_AI_RESPONSE_MODEL, TEST_RESPONSE_MODEL),
                                    satisfies(
                                        GEN_AI_RESPONSE_FINISH_REASONS,
                                        val -> val.containsExactly("stop")),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                                    equalTo(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L),
                                    equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 4L),
                                    equalTo(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)))));
  }

  private static Stream<Arguments> withOptionsCases() {
    return Stream.of(
        argumentSet(
            "client",
            (Function<OpenAIClient, ResponseService>)
                client -> testHelper.withoutRetries(client).responses(),
            (Function<OpenAIClientAsync, ResponseServiceAsync>)
                client -> testHelper.withoutRetries(client).responses()),
        argumentSet(
            "response service",
            (Function<OpenAIClient, ResponseService>)
                client -> testHelper.withoutRetries(client.responses()),
            (Function<OpenAIClientAsync, ResponseServiceAsync>)
                client -> testHelper.withoutRetries(client.responses())));
  }

  @ParameterizedTest
  @MethodSource("requestOptionsCases")
  void withoutParams(boolean explicitOptions) {
    // create() without params was added in SDK 2.x
    assumeTrue(testLatestDeps());
    OpenAIClient client =
        wrap(
            OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    cleanup.deferCleanup(client::close);
    cleanup.deferCleanup(clientAsync::close);

    Throwable thrown =
        catchThrowable(
            () ->
                doResponses(
                    (service, p) -> testHelper.createWithoutParams(service, explicitOptions),
                    (service, p) -> testHelper.createWithoutParams(service, explicitOptions),
                    ResponseCreateParams.builder().build(),
                    client,
                    clientAsync));
    assertThat(thrown).isInstanceOf(OpenAIIoException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            span.hasName("chat")
                                .hasKind(SpanKind.CLIENT)
                                .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, OpenAIIoException.class.getName()),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT)))));

    getTesting().waitAndAssertLogRecords(genAiClientExceptionLogs(thrown));
  }

  @ParameterizedTest
  @MethodSource("requestOptionsCases")
  void withoutParamsStream(boolean explicitOptions) {
    // createStreaming() without params was added in SDK 2.x
    assumeTrue(testLatestDeps());
    OpenAIClient client =
        wrap(
            OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:" + PortUtils.UNUSABLE_PORT + "/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    cleanup.deferCleanup(client::close);
    cleanup.deferCleanup(clientAsync::close);

    Throwable thrown =
        catchThrowable(
            () ->
                doResponsesStreaming(
                    (service, p) ->
                        testHelper.createStreamingWithoutParams(service, explicitOptions),
                    (service, p) ->
                        testHelper.createStreamingWithoutParams(service, explicitOptions),
                    ResponseCreateParams.builder().build(),
                    client,
                    clientAsync));
    assertThat(thrown).isInstanceOf(OpenAIIoException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            span.hasName("chat")
                                .hasKind(SpanKind.CLIENT)
                                .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(ERROR_TYPE, OpenAIIoException.class.getName()),
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_STREAM, true)))));

    getTesting().waitAndAssertLogRecords(genAiClientExceptionLogs(thrown));
  }

  private static SpanDataAssert assertResponsesSpan(SpanDataAssert span) {
    return span.hasName("chat " + TEST_MODEL).hasKind(SpanKind.CLIENT);
  }

  private Response doResponses(ResponseCreateParams params) {
    return doResponses(params, getClient(), getClientAsync());
  }

  private Response doResponses(
      ResponseCreateParams params, OpenAIClient client, OpenAIClientAsync clientAsync) {
    return doResponses(
        ResponseService::create, ResponseServiceAsync::create, params, client, clientAsync);
  }

  private Response doResponses(
      BiFunction<ResponseService, ResponseCreateParams, Response> sync,
      BiFunction<ResponseServiceAsync, ResponseCreateParams, CompletableFuture<Response>> async,
      ResponseCreateParams params,
      OpenAIClient client,
      OpenAIClientAsync clientAsync) {
    switch (testType) {
      case SYNC:
        return sync.apply(client.responses(), params);
      case SYNC_FROM_ASYNC:
        return sync.apply(clientAsync.sync().responses(), params);
      case ASYNC:
      case ASYNC_FROM_SYNC:
        OpenAIClientAsync cl = testType == TestType.ASYNC ? clientAsync : client.async();
        try {
          return async
              .apply(cl.responses(), params)
              .thenApply(
                  res -> {
                    assertThat(Span.fromContextOrNull(Context.current())).isNull();
                    return res;
                  })
              .join();
        } catch (CompletionException e) {
          if (e.getCause() instanceof RuntimeException) {
            throw (RuntimeException) e.getCause();
          }
          throw e;
        }
    }
    throw new AssertionError();
  }

  private List<ResponseStreamEvent> doResponsesStreaming(ResponseCreateParams params) {
    return doResponsesStreaming(params, getClient(), getClientAsync());
  }

  private List<ResponseStreamEvent> doResponsesStreaming(
      ResponseCreateParams params, OpenAIClient client, OpenAIClientAsync clientAsync) {
    return doResponsesStreaming(
        ResponseService::createStreaming,
        ResponseServiceAsync::createStreaming,
        params,
        client,
        clientAsync);
  }

  private List<ResponseStreamEvent> doResponsesStreaming(
      BiFunction<ResponseService, ResponseCreateParams, StreamResponse<ResponseStreamEvent>> sync,
      BiFunction<
              ResponseServiceAsync, ResponseCreateParams, AsyncStreamResponse<ResponseStreamEvent>>
          async,
      ResponseCreateParams params) {
    return doResponsesStreaming(sync, async, params, getClient(), getClientAsync());
  }

  private List<ResponseStreamEvent> doResponsesStreaming(
      BiFunction<ResponseService, ResponseCreateParams, StreamResponse<ResponseStreamEvent>> sync,
      BiFunction<
              ResponseServiceAsync, ResponseCreateParams, AsyncStreamResponse<ResponseStreamEvent>>
          async,
      ResponseCreateParams params,
      OpenAIClient client,
      OpenAIClientAsync clientAsync) {
    switch (testType) {
      case SYNC:
        try (StreamResponse<ResponseStreamEvent> result = sync.apply(client.responses(), params)) {
          return result.stream().collect(toList());
        }
      case SYNC_FROM_ASYNC:
        try (StreamResponse<ResponseStreamEvent> result =
            sync.apply(clientAsync.sync().responses(), params)) {
          return result.stream().collect(toList());
        }
      case ASYNC:
      case ASYNC_FROM_SYNC:
        {
          OpenAIClientAsync cl = testType == TestType.ASYNC ? clientAsync : client.async();
          AsyncStreamResponse<ResponseStreamEvent> stream = async.apply(cl.responses(), params);
          List<ResponseStreamEvent> result = new ArrayList<>();
          stream.subscribe(result::add);
          try {
            stream.onCompleteFuture().join();
          } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw e;
          }
          return result;
        }
    }
    throw new AssertionError();
  }
}
