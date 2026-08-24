/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class GenAiAttributesExtractorTest {

  @Test
  void fallsBackToExceptionClassForErrorType() {
    AttributesExtractor<String, String> extractor =
        GenAiAttributesExtractor.create(new TestGetter());
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes, Context.root(), "request", "response", new IllegalStateException("failure"));

    assertThat(attributes.build()).containsEntry(ERROR_TYPE, IllegalStateException.class.getName());
  }

  private static final class TestGetter implements GenAiAttributesGetter<String, String> {
    @Override
    public String getOperationName(String request) {
      return "chat";
    }

    @Override
    public String getSystem(String request) {
      return "test";
    }

    @Override
    @Nullable
    public String getRequestModel(String request) {
      return null;
    }

    @Override
    @Nullable
    public Long getRequestSeed(String request) {
      return null;
    }

    @Override
    @Nullable
    public List<String> getRequestEncodingFormats(String request) {
      return null;
    }

    @Override
    @Nullable
    public Double getRequestFrequencyPenalty(String request) {
      return null;
    }

    @Override
    @Nullable
    public Long getRequestMaxTokens(String request) {
      return null;
    }

    @Override
    @Nullable
    public Double getRequestPresencePenalty(String request) {
      return null;
    }

    @Override
    @Nullable
    public List<String> getRequestStopSequences(String request) {
      return null;
    }

    @Override
    @Nullable
    public Double getRequestTemperature(String request) {
      return null;
    }

    @Override
    @Nullable
    public Double getRequestTopK(String request) {
      return null;
    }

    @Override
    @Nullable
    public Double getRequestTopP(String request) {
      return null;
    }

    @Override
    @Nullable
    public List<String> getResponseFinishReasons(String request, @Nullable String response) {
      return null;
    }

    @Override
    @Nullable
    public String getResponseId(String request, @Nullable String response) {
      return null;
    }

    @Override
    @Nullable
    public String getResponseModel(String request, @Nullable String response) {
      return null;
    }

    @Override
    @Nullable
    public Long getUsageInputTokens(String request, @Nullable String response) {
      return null;
    }

    @Override
    @Nullable
    public Long getUsageOutputTokens(String request, @Nullable String response) {
      return null;
    }
  }
}
