/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_USAGE_REASONING_OUTPUT_TOKENS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenAiAttributesExtractorTest {

  private static final AttributeKey<Boolean> GEN_AI_REQUEST_STREAM =
      booleanKey("gen_ai.request.stream");
  private static final AttributeKey<List<String>> GEN_AI_REQUEST_ENCODING_FORMATS =
      stringArrayKey("gen_ai.request.encoding_formats");
  private static final AttributeKey<List<String>> GEN_AI_REQUEST_STOP_SEQUENCES =
      stringArrayKey("gen_ai.request.stop_sequences");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void extractsStreamingOnStart(boolean streaming) {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    Request request = new Request(streaming);

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    AttributesBuilder expected =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "chat")
            .put(GEN_AI_PROVIDER_NAME, "test")
            .put(GEN_AI_REQUEST_MODEL, "test-model")
            .put(GEN_AI_REQUEST_ENCODING_FORMATS, emptyList())
            .put(GEN_AI_REQUEST_STOP_SEQUENCES, emptyList());
    if (streaming) {
      expected.put(GEN_AI_REQUEST_STREAM, true);
    }
    assertThat(attributes.build()).isEqualTo(expected.build());
  }

  @Test
  void fallsBackToExceptionClassForErrorType() {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes, Context.root(), new Request(false), null, new IllegalStateException("failure"));

    assertThat(attributes.build()).containsEntry(ERROR_TYPE, IllegalStateException.class.getName());
  }

  @Test
  void usesGetterErrorTypeBeforeExceptionClass() {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(
            new TestGetter() {
              @Override
              public String getErrorType(Request request, Response response, Throwable error) {
                return "provider_error";
              }
            });
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes, Context.root(), new Request(false), null, new IllegalStateException("failure"));

    assertThat(attributes.build()).containsEntry(ERROR_TYPE, "provider_error");
  }

  @Test
  void extractsDetailedTokenUsageOnEnd() {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), new Request(false), new Response(12L, 4L), null);

    assertThat(attributes.build())
        .isEqualTo(
            Attributes.builder()
                .put(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 12L)
                .put(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 4L)
                .build());
  }

  @Test
  void preservesExplicitZeroDetailedTokenUsageOnEnd() {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), new Request(false), new Response(0L, 0L), null);

    assertThat(attributes.build())
        .isEqualTo(
            Attributes.builder()
                .put(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, 0L)
                .put(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 0L)
                .build());
  }

  @Test
  void omitsDetailedTokenUsageWhenResponseIsUnavailable() {
    AttributesExtractor<Request, Response> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), new Request(false), null, null);

    assertThat(attributes.build()).isEmpty();
  }

  private static final class Request {
    private final boolean streaming;

    private Request(boolean streaming) {
      this.streaming = streaming;
    }
  }

  private static final class Response {
    private final Long cacheReadInputTokens;
    private final Long reasoningOutputTokens;

    private Response(Long cacheReadInputTokens, Long reasoningOutputTokens) {
      this.cacheReadInputTokens = cacheReadInputTokens;
      this.reasoningOutputTokens = reasoningOutputTokens;
    }
  }

  private static class TestGetter implements GenAiAttributesGetter<Request, Response> {

    @Override
    public String getOperationName(Request request) {
      return "chat";
    }

    @Override
    public String getSystem(Request request) {
      return "test";
    }

    @Override
    public String getRequestModel(Request request) {
      return "test-model";
    }

    @Override
    public boolean isRequestStreaming(Request request) {
      return request.streaming;
    }

    @Override
    public Long getRequestSeed(Request request) {
      return null;
    }

    @Override
    public List<String> getRequestEncodingFormats(Request request) {
      return emptyList();
    }

    @Override
    public Double getRequestFrequencyPenalty(Request request) {
      return null;
    }

    @Override
    public Long getRequestMaxTokens(Request request) {
      return null;
    }

    @Override
    public Double getRequestPresencePenalty(Request request) {
      return null;
    }

    @Override
    public List<String> getRequestStopSequences(Request request) {
      return emptyList();
    }

    @Override
    public Double getRequestTemperature(Request request) {
      return null;
    }

    @Override
    public Double getRequestTopK(Request request) {
      return null;
    }

    @Override
    public Double getRequestTopP(Request request) {
      return null;
    }

    @Override
    public List<String> getResponseFinishReasons(Request request, @Nullable Response response) {
      return emptyList();
    }

    @Override
    public String getResponseId(Request request, @Nullable Response response) {
      return null;
    }

    @Override
    public String getResponseModel(Request request, @Nullable Response response) {
      return null;
    }

    @Override
    public Long getUsageInputTokens(Request request, @Nullable Response response) {
      return null;
    }

    @Override
    public Long getUsageCacheReadInputTokens(Request request, @Nullable Response response) {
      return response == null ? null : response.cacheReadInputTokens;
    }

    @Nullable
    @Override
    public Long getUsageOutputTokens(Request request, @Nullable Response response) {
      return null;
    }

    @Nullable
    @Override
    public Long getUsageReasoningOutputTokens(Request request, @Nullable Response response) {
      return response == null ? null : response.reasoningOutputTokens;
    }
  }
}
