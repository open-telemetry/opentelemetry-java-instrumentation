/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
                                    equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.4),
                                    equalTo(GEN_AI_REQUEST_STREAM, true),
                                    equalTo(GEN_AI_RESPONSE_ID, "resp_14783streamerror"),
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
                .baseUrl("http://localhost:9999/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:9999/v5")
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
                .baseUrl("http://localhost:9999/v5")
                .apiKey("testing")
                .maxRetries(0)
                .build());
    OpenAIClientAsync clientAsync =
        wrap(
            OpenAIOkHttpClientAsync.builder()
                .baseUrl("http://localhost:9999/v5")
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
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                    equalTo(GEN_AI_REQUEST_MODEL, TEST_MODEL),
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
    switch (testType) {
      case SYNC:
        return client.responses().create(params);
      case SYNC_FROM_ASYNC:
        return clientAsync.sync().responses().create(params);
      case ASYNC:
      case ASYNC_FROM_SYNC:
        OpenAIClientAsync cl = testType == TestType.ASYNC ? clientAsync : client.async();
        try {
          return cl.responses()
              .create(params)
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
    switch (testType) {
      case SYNC:
        try (StreamResponse<ResponseStreamEvent> result =
            client.responses().createStreaming(params)) {
          return result.stream().collect(toList());
        }
      case SYNC_FROM_ASYNC:
        try (StreamResponse<ResponseStreamEvent> result =
            clientAsync.sync().responses().createStreaming(params)) {
          return result.stream().collect(toList());
        }
      case ASYNC:
      case ASYNC_FROM_SYNC:
        {
          OpenAIClientAsync cl = testType == TestType.ASYNC ? clientAsync : client.async();
          AsyncStreamResponse<ResponseStreamEvent> stream = cl.responses().createStreaming(params);
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
