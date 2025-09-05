/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_ENCODING_FORMATS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EMBEDDINGS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.INPUT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.errors.OpenAIIoException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public abstract class AbstractEmbeddingsTest extends AbstractOpenAiTest {
  private static final String MODEL = "text-embedding-3-small";

  protected final CreateEmbeddingResponse doEmbeddings(EmbeddingCreateParams request) {
    return doEmbeddings(request, getClient(), getClientAsync());
  }

  protected final CreateEmbeddingResponse doEmbeddings(
      EmbeddingCreateParams request, OpenAIClient client, OpenAIClientAsync clientAsync) {
    switch (testType) {
      case SYNC:
        return client.embeddings().create(request);
      case SYNC_FROM_ASYNC:
        return clientAsync.sync().embeddings().create(request);
      case ASYNC:
      case ASYNC_FROM_SYNC:
        OpenAIClientAsync cl = testType == TestType.ASYNC ? clientAsync : client.async();
        try {
          return cl.embeddings()
              .create(request)
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

  @Test
  void basic() {
    String text = "South Atlantic Ocean.";

    EmbeddingCreateParams request =
        EmbeddingCreateParams.builder()
            .model(MODEL)
            .inputOfArrayOfStrings(singletonList(text))
            .build();
    CreateEmbeddingResponse response = doEmbeddings(request);

    assertThat(response.data()).hasSize(1);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            span.hasName("embeddings text-embedding-3-small")
                                .hasKind(SpanKind.CLIENT)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                    equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                    equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 4),
                                    // Newer versions of the library populate base64 when unset by
                                    // the user.
                                    satisfies(
                                        GEN_AI_REQUEST_ENCODING_FORMATS,
                                        val ->
                                            val.satisfiesAnyOf(
                                                v -> assertThat(v).isNull(),
                                                v ->
                                                    assertThat(v)
                                                        .isEqualTo(singletonList("base64"))))))));

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
                                            equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(4.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)))));
  }

  @Test
  void allTheClientOptions() {
    String text = "South Atlantic Ocean.";

    EmbeddingCreateParams request =
        EmbeddingCreateParams.builder()
            .model(MODEL)
            .encodingFormat(EmbeddingCreateParams.EncodingFormat.BASE64)
            .inputOfArrayOfStrings(singletonList(text))
            .build();
    CreateEmbeddingResponse response = doEmbeddings(request);

    assertThat(response.data()).hasSize(1);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            span.hasName("embeddings text-embedding-3-small")
                                .hasKind(SpanKind.CLIENT)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                    equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                    equalTo(
                                        GEN_AI_REQUEST_ENCODING_FORMATS, singletonList("base64")),
                                    equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                    equalTo(GEN_AI_USAGE_INPUT_TOKENS, 4)))));

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
                                            equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(4.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                            equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)))));
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

    EmbeddingCreateParams request =
        EmbeddingCreateParams.builder()
            .model(MODEL)
            .inputOfArrayOfStrings(singletonList(INPUT))
            .build();

    Throwable thrown = catchThrowable(() -> doEmbeddings(request, client, clientAsync));
    assertThat(thrown).isInstanceOf(OpenAIIoException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    maybeWithTransportSpan(
                        span ->
                            span.hasName("embeddings text-embedding-3-small")
                                .hasKind(SpanKind.CLIENT)
                                .hasException(thrown)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                    equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                    equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                    // Newer versions of the library populate base64 when unset by
                                    // the user.
                                    satisfies(
                                        GEN_AI_REQUEST_ENCODING_FORMATS,
                                        val ->
                                            val.satisfiesAnyOf(
                                                v -> assertThat(v).isNull(),
                                                v ->
                                                    assertThat(v)
                                                        .isEqualTo(singletonList("base64"))))))));

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
                                            equalTo(GEN_AI_OPERATION_NAME, EMBEDDINGS),
                                            equalTo(GEN_AI_REQUEST_MODEL, MODEL)))));
  }
}
