/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpExperimentalAttributesExtractorTest {

  @Mock HttpClientAttributesGetter<String, String> clientGetter;
  @Mock HttpServerAttributesGetter<String, String> serverGetter;

  @Test
  void shouldExtractRequestAndResponseSizes_client() {
    runTest(clientGetter, HttpExperimentalAttributesExtractor.create(clientGetter));
  }

  @Test
  void shouldExtractRequestAndResponseSizes_server() {
    runTest(serverGetter, HttpExperimentalAttributesExtractor.create(serverGetter));
  }

  void runTest(
      HttpCommonAttributesGetter<String, String> getter,
      AttributesExtractor<String, String> extractor) {

    when(getter.getHttpRequestHeader("request", "content-length")).thenReturn(singletonList("123"));
    when(getter.getHttpResponseHeader("request", "response", "content-length"))
        .thenReturn(singletonList("42"));

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), "request");
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, Context.root(), "request", "response", null);
    assertThat(attributes.build())
        .containsOnly(
            entry(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, 123L),
            entry(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, 42L));
  }
}
