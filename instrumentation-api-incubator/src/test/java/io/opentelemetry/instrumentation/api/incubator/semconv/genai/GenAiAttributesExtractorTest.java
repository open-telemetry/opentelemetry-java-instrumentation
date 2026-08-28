/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenAiAttributesExtractorTest {

  private static final AttributeKey<Boolean> GEN_AI_REQUEST_STREAM =
      booleanKey("gen_ai.request.stream");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void extractsStreamingOnStart(boolean streaming) {
    AttributesExtractor<Request, Void> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    Request request = new Request(streaming);

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    AttributesBuilder expected =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "chat")
            .put(GEN_AI_PROVIDER_NAME, "test")
            .put(GEN_AI_REQUEST_MODEL, "test-model");
    if (streaming) {
      expected.put(GEN_AI_REQUEST_STREAM, true);
    }
    assertThat(attributes.build()).isEqualTo(expected.build());
  }

  private static final class Request {
    private final boolean streaming;

    private Request(boolean streaming) {
      this.streaming = streaming;
    }
  }

  private static final class TestGetter implements GenAiAttributesGetter<Request, Void> {

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

    @Nullable
    @Override
    public Long getRequestSeed(Request request) {
      return null;
    }

    @Nullable
    @Override
    public List<String> getRequestEncodingFormats(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Double getRequestFrequencyPenalty(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Long getRequestMaxTokens(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Double getRequestPresencePenalty(Request request) {
      return null;
    }

    @Nullable
    @Override
    public List<String> getRequestStopSequences(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Double getRequestTemperature(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Double getRequestTopK(Request request) {
      return null;
    }

    @Nullable
    @Override
    public Double getRequestTopP(Request request) {
      return null;
    }

    @Override
    public List<String> getResponseFinishReasons(Request request, @Nullable Void response) {
      return emptyList();
    }

    @Nullable
    @Override
    public String getResponseId(Request request, @Nullable Void response) {
      return null;
    }

    @Nullable
    @Override
    public String getResponseModel(Request request, @Nullable Void response) {
      return null;
    }

    @Nullable
    @Override
    public Long getUsageInputTokens(Request request, @Nullable Void response) {
      return null;
    }

    @Nullable
    @Override
    public Long getUsageOutputTokens(Request request, @Nullable Void response) {
      return null;
    }
  }
}
