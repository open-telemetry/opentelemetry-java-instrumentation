/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_ENCODING_FORMATS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.errors.RateLimitException;
import com.openai.errors.SseException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.ResponseCreateParams;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceErrorTest {

  private static final String MODEL = "test-model";
  private static final String INSTRUMENTATION_NAME = OpenAITelemetryBuilder.INSTRUMENTATION_NAME;

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @RegisterExtension
  private static final WireMockExtension server =
      WireMockExtension.newInstance().options(options().dynamicPort()).build();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private enum Operation {
    CHAT("chat", "/chat/completions"),
    CHAT_STREAM("chat", "/chat/completions"),
    EMBEDDINGS("embeddings", "/embeddings"),
    RESPONSES("chat", "/responses");

    final String name;
    final String path;

    Operation(String name, String path) {
      this.name = name;
      this.path = path;
    }
  }

  private static Stream<Arguments> httpErrorPayloads() {
    return Stream.of(
        Arguments.of(400, error("\"context_length_exceeded\""), "context_length_exceeded"),
        Arguments.of(429, error("\"insufficient_quota\""), "insufficient_quota"),
        Arguments.of(400, error("null"), BadRequestException.class.getName()),
        Arguments.of(400, error("42"), BadRequestException.class.getName()),
        Arguments.of(400, error("{}"), BadRequestException.class.getName()),
        Arguments.of(400, error("\"\""), ""),
        Arguments.of(400, error("\"   \""), "   "),
        Arguments.of(400, error("\"provider_specific_code\""), "provider_specific_code"),
        Arguments.of(400, error("\" provider_specific_code \""), " provider_specific_code "),
        Arguments.of(
            400,
            "{\"error\":{\"message\":\"private message\",\"type\":\"invalid_request_error\"}}",
            BadRequestException.class.getName()),
        Arguments.of(400, "{\"error\":\"unknown shape\"}", BadRequestException.class.getName()));
  }

  private static String error(String code) {
    return "{\"error\":{\"code\":"
        + code
        + ",\"message\":\"private message\",\"type\":\"invalid_request_error\",\"param\":null}}";
  }

  @ParameterizedTest
  @MethodSource("httpErrorPayloads")
  void serviceErrorPayload(int status, String body, String expectedErrorType) {
    assertServiceError(Operation.CHAT, false, status, body, expectedErrorType);
  }

  @ParameterizedTest
  @MethodSource("serviceErrorCallPaths")
  void serviceErrorCallPath(
      Operation operation, boolean async, String code, String expectedErrorType) {
    assertServiceError(operation, async, 400, error(code), expectedErrorType);
  }

  private static Stream<Arguments> serviceErrorCallPaths() {
    return Stream.of(
        Arguments.of(Operation.CHAT, true, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.CHAT, true, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.CHAT_STREAM, false, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.CHAT_STREAM, false, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.CHAT_STREAM, true, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.CHAT_STREAM, true, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.EMBEDDINGS, false, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.EMBEDDINGS, false, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.EMBEDDINGS, true, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.EMBEDDINGS, true, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.RESPONSES, false, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.RESPONSES, false, "42", BadRequestException.class.getName()),
        Arguments.of(
            Operation.RESPONSES, true, "\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of(Operation.RESPONSES, true, "42", BadRequestException.class.getName()));
  }

  private static void assertServiceError(
      Operation operation, boolean async, int status, String body, String expectedErrorType) {
    server.stubFor(
        post(urlEqualTo(operation.path))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    Throwable thrown = catchThrowable(() -> execute(operation, async));
    if (thrown instanceof CompletionException) {
      thrown = thrown.getCause();
    }
    assertThat(thrown)
        .isInstanceOf(status == 429 ? RateLimitException.class : BadRequestException.class);
    assertTelemetry(operation, expectedErrorType, false);
  }

  @ParameterizedTest
  @MethodSource("asyncStreamTerminalErrors")
  void asyncStreamTerminalError(String code, String expectedErrorType) {
    server.stubFor(
        post(urlEqualTo("/chat/completions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        "data: {\"id\":\"chat-1\",\"object\":\"chat.completion.chunk\","
                            + "\"created\":0,\"model\":\"test-model\",\"choices\":[]}\n\n"
                            + "data: "
                            + error(code)
                            + "\n\n")));

    Throwable thrown = catchThrowable(() -> execute(Operation.CHAT_STREAM, true));
    assertThat(thrown)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(SseException.class);
    assertTelemetry(Operation.CHAT_STREAM, expectedErrorType, true);
  }

  private static Stream<Arguments> asyncStreamTerminalErrors() {
    return Stream.of(
        Arguments.of("\"rate_limit_exceeded\"", "rate_limit_exceeded"),
        Arguments.of("\"provider_specific_code\"", "provider_specific_code"),
        Arguments.of("null", SseException.class.getName()),
        Arguments.of("42", SseException.class.getName()));
  }

  private static void execute(Operation operation, boolean async) {
    OpenAITelemetry telemetry = OpenAITelemetry.builder(testing.getOpenTelemetry()).build();
    ChatCompletionCreateParams chat =
        ChatCompletionCreateParams.builder().model(MODEL).addUserMessage("hello").build();
    EmbeddingCreateParams embeddings =
        EmbeddingCreateParams.builder()
            .model(MODEL)
            .input("hello")
            .encodingFormat(EmbeddingCreateParams.EncodingFormat.BASE64)
            .build();
    ResponseCreateParams responses =
        ResponseCreateParams.builder().model(MODEL).input("hello").build();
    if (async) {
      OpenAIClientAsync client =
          telemetry.instrument(
              OpenAIOkHttpClientAsync.builder()
                  .baseUrl(server.baseUrl())
                  .apiKey("unused")
                  .maxRetries(0)
                  .build());
      cleanup.deferCleanup(client::close);
      switch (operation) {
        case CHAT:
          client.chat().completions().create(chat).join();
          break;
        case EMBEDDINGS:
          client.embeddings().create(embeddings).join();
          break;
        case RESPONSES:
          client.responses().create(responses).join();
          break;
        case CHAT_STREAM:
          AsyncStreamResponse<ChatCompletionChunk> stream =
              client.chat().completions().createStreaming(chat);
          cleanup.deferCleanup(stream::close);
          stream.subscribe(chunk -> {});
          stream.onCompleteFuture().join();
          break;
      }
    } else {
      OpenAIClient client =
          telemetry.instrument(
              OpenAIOkHttpClient.builder()
                  .baseUrl(server.baseUrl())
                  .apiKey("unused")
                  .maxRetries(0)
                  .build());
      cleanup.deferCleanup(client::close);
      switch (operation) {
        case CHAT:
          client.chat().completions().create(chat);
          break;
        case EMBEDDINGS:
          client.embeddings().create(embeddings);
          break;
        case RESPONSES:
          client.responses().create(responses);
          break;
        case CHAT_STREAM:
          try (StreamResponse<ChatCompletionChunk> stream =
              client.chat().completions().createStreaming(chat)) {
            stream.stream().forEach(chunk -> {});
          }
          break;
      }
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void assertTelemetry(
      Operation operation, String expectedErrorType, boolean hasResponse) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(operation.name + " " + MODEL)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatusSatisfying(status -> status.hasCode(StatusCode.ERROR))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ERROR_TYPE, expectedErrorType),
                            equalTo(GEN_AI_PROVIDER_NAME, "openai"),
                            equalTo(GEN_AI_OPERATION_NAME, operation.name),
                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                            equalTo(
                                GEN_AI_REQUEST_ENCODING_FORMATS,
                                operation == Operation.EMBEDDINGS ? singletonList("base64") : null),
                            equalTo(
                                GEN_AI_REQUEST_STREAM,
                                operation == Operation.CHAT_STREAM ? true : null),
                            equalTo(GEN_AI_RESPONSE_ID, hasResponse ? "chat-1" : null),
                            equalTo(GEN_AI_RESPONSE_MODEL, hasResponse ? MODEL : null))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("gen_ai.client.operation.duration")
                .hasUnit("s")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasSumGreaterThan(0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(ERROR_TYPE, expectedErrorType),
                                        equalTo(GEN_AI_PROVIDER_NAME, "openai"),
                                        equalTo(GEN_AI_OPERATION_NAME, operation.name),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(
                                            GEN_AI_RESPONSE_MODEL, hasResponse ? MODEL : null)))));
  }
}
