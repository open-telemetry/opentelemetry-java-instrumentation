/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.UrlIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpExperimentalAttributesExtractorTest {

  @Mock HttpClientExperimentalAttributesGetter<String, String> clientGetter;
  @Mock HttpServerAttributesGetter<String, String> serverGetter;

  @Test
  void shouldExtractRequestAndResponseSizes_client() {
    when(clientGetter.getUrlTemplate("request")).thenReturn("template");
    runTest(
        clientGetter,
        HttpExperimentalAttributesExtractor.create(clientGetter),
        Collections.singletonMap(UrlIncubatingAttributes.URL_TEMPLATE, "template"));
  }

  @Test
  void shouldExtractRequestAndResponseSizes_server() {
    runTest(
        serverGetter,
        HttpExperimentalAttributesExtractor.create(serverGetter),
        Collections.emptyMap());
  }

  void runTest(
      HttpCommonAttributesGetter<String, String> getter,
      AttributesExtractor<String, String> extractor,
      Map<AttributeKey<?>, ?> expected) {

    when(getter.getHttpRequestHeader("request", "content-length")).thenReturn(singletonList("123"));
    when(getter.getHttpResponseHeader("request", "response", "content-length"))
        .thenReturn(singletonList("42"));

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), "request");
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, Context.root(), "request", "response", null);
    Map<AttributeKey<?>, Object> expectedAttributes = new HashMap<>(expected);
    expectedAttributes.put(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, 123L);
    expectedAttributes.put(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, 42L);
    assertThat(attributes.build().asMap()).containsExactlyInAnyOrderEntriesOf(expectedAttributes);
  }
}
