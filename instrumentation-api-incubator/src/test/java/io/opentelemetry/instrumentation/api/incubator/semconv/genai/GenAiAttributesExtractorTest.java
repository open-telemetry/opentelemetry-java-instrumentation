/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
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

  @Test
  void usesGetterErrorTypeBeforeExceptionClass() {
    AttributesExtractor<String, String> extractor =
        GenAiAttributesExtractor.create(
            new TestGetter() {
              @Override
              public String getErrorType(String request, String response, Throwable error) {
                return "provider_error";
              }
            });
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes, Context.root(), "request", "response", new IllegalStateException("failure"));

    assertThat(attributes.build()).containsEntry(ERROR_TYPE, "provider_error");
  }

  private static class TestGetter implements GenAiAttributesGetter<String, String> {
    @Override
    public String getOperationName(String request) {
      return "chat";
    }

    @Override
    public String getSystem(String request) {
      return "test";
    }

    @Override
    public String getRequestModel(String request) {
      return null;
    }

    @Override
    public Long getRequestSeed(String request) {
      return null;
    }

    @Override
    public List<String> getRequestEncodingFormats(String request) {
      return emptyList();
    }

    @Override
    public Double getRequestFrequencyPenalty(String request) {
      return null;
    }

    @Override
    public Long getRequestMaxTokens(String request) {
      return null;
    }

    @Override
    public Double getRequestPresencePenalty(String request) {
      return null;
    }

    @Override
    public List<String> getRequestStopSequences(String request) {
      return emptyList();
    }

    @Override
    public Double getRequestTemperature(String request) {
      return null;
    }

    @Override
    public Double getRequestTopK(String request) {
      return null;
    }

    @Override
    public Double getRequestTopP(String request) {
      return null;
    }

    @Override
    public List<String> getResponseFinishReasons(String request, String response) {
      return emptyList();
    }

    @Override
    public String getResponseId(String request, String response) {
      return null;
    }

    @Override
    public String getResponseModel(String request, String response) {
      return null;
    }

    @Override
    public Long getUsageInputTokens(String request, String response) {
      return null;
    }

    @Override
    public Long getUsageOutputTokens(String request, String response) {
      return null;
    }
  }
}
