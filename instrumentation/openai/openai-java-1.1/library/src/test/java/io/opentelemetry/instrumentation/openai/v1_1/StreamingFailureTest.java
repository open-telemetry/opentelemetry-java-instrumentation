/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import com.openai.errors.SseException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Iterator;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class StreamingFailureTest {

  private static final String CHUNK =
      "data: {\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\",\"created\":0,"
          + "\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}\n\n";
  private static final String ERROR =
      "data: {\"error\":{\"message\":\"Stream failed\",\"type\":\"server_error\"}}\n\n";
  private static final ChatCompletionCreateParams PARAMS =
      ChatCompletionCreateParams.builder().model("gpt-4o-mini").addUserMessage("Hello").build();

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension server =
      WireMockExtension.newInstance().options(options().dynamicPort()).build();

  private static OpenAIClient client() {
    return OpenAITelemetry.create(testing.getOpenTelemetry())
        .wrap(OpenAIOkHttpClient.builder().baseUrl(server.baseUrl()).apiKey("unused").build());
  }

  private static void stubStream(String ending) {
    server.stubFor(
        post(urlEqualTo("/chat/completions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(CHUNK + ending)));
  }

  @ParameterizedTest
  @EnumSource(Consumption.class)
  void terminalFailure(Consumption consumption) {
    stubStream(ERROR);

    Throwable thrown;
    try (StreamResponse<ChatCompletionChunk> response =
        client().chat().completions().createStreaming(PARAMS)) {
      thrown = catchThrowable(() -> consumption.consume(response.stream()));
      assertThat(thrown).isInstanceOf(SseException.class).hasMessageContaining("Stream failed");
      // The failure itself must end the operation, before resource cleanup.
      assertThat(testing.spans()).hasSize(1);
    }

    assertCompletion(thrown);
  }

  @Test
  void asyncTerminalFailure() {
    stubStream(ERROR);

    Throwable thrown;
    AsyncStreamResponse<ChatCompletionChunk> response =
        client().async().chat().completions().createStreaming(PARAMS);
    try {
      response.subscribe(chunk -> {});
      Throwable completion = catchThrowable(() -> response.onCompleteFuture().join());
      assertThat(completion).isInstanceOf(CompletionException.class);
      thrown = completion.getCause();
      assertThat(thrown).isInstanceOf(SseException.class);
    } finally {
      response.close();
    }

    assertCompletion(thrown);
  }

  @ParameterizedTest
  @EnumSource(Consumption.class)
  void normalCompletion(Consumption consumption) {
    stubStream("data: [DONE]\n\n");

    try (StreamResponse<ChatCompletionChunk> response =
        client().chat().completions().createStreaming(PARAMS)) {
      consumption.consume(response.stream());
      assertThat(testing.spans()).hasSize(1);
    }

    assertCompletion(null);
  }

  @Test
  void earlyClose() {
    stubStream(ERROR);

    try (StreamResponse<ChatCompletionChunk> response =
        client().chat().completions().createStreaming(PARAMS)) {
      assertThat(response.stream().findFirst()).isPresent();
    }

    assertCompletion(null);
  }

  @Test
  void consumerFailure() {
    stubStream("data: [DONE]\n\n");
    IllegalStateException failure = new IllegalStateException("Consumer failed");

    try (StreamResponse<ChatCompletionChunk> response =
        client().chat().completions().createStreaming(PARAMS)) {
      assertThat(
              catchThrowable(
                  () ->
                      response.stream()
                          .forEach(
                              chunk -> {
                                throw failure;
                              })))
          .isSameAs(failure);
    }

    assertCompletion(null);
  }

  private static void assertCompletion(Throwable failure) {
    String errorType = failure == null ? null : failure.getClass().getName();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("chat gpt-4o-mini")
                      .hasKind(SpanKind.CLIENT)
                      .hasException(emitExceptionAsSpanEvents() ? failure : null)
                      .hasStatusSatisfying(
                          status ->
                              status.hasCode(failure == null ? StatusCode.UNSET : StatusCode.ERROR))
                      .hasAttributesSatisfyingExactly(
                          equalTo(GEN_AI_PROVIDER_NAME, "openai"),
                          equalTo(GEN_AI_OPERATION_NAME, "chat"),
                          equalTo(GEN_AI_REQUEST_MODEL, "gpt-4o-mini"),
                          equalTo(GEN_AI_REQUEST_STREAM, true),
                          equalTo(GEN_AI_RESPONSE_ID, "chatcmpl-test"),
                          equalTo(GEN_AI_RESPONSE_MODEL, "gpt-4o-mini"),
                          equalTo(ERROR_TYPE, errorType));
                }));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.openai-java-1.1",
        metric ->
            metric
                .hasName("gen_ai.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(GEN_AI_PROVIDER_NAME, "openai"),
                                        equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                        equalTo(GEN_AI_REQUEST_MODEL, "gpt-4o-mini"),
                                        equalTo(GEN_AI_RESPONSE_MODEL, "gpt-4o-mini"),
                                        equalTo(ERROR_TYPE, errorType)))));
  }

  enum Consumption {
    COLLECT,
    FOR_EACH,
    ITERATOR,
    SPLITERATOR,
    PARALLEL;

    void consume(Stream<ChatCompletionChunk> stream) {
      switch (this) {
        case COLLECT:
          assertThat(stream.collect(toList())).hasSize(1);
          break;
        case FOR_EACH:
          stream.forEach(chunk -> {});
          break;
        case ITERATOR:
          Iterator<ChatCompletionChunk> iterator = stream.iterator();
          while (iterator.hasNext()) {
            iterator.next();
          }
          break;
        case SPLITERATOR:
          stream.spliterator().forEachRemaining(chunk -> {});
          break;
        case PARALLEL:
          assertThat(stream.parallel().collect(toList())).hasSize(1);
          break;
      }
    }
  }
}
