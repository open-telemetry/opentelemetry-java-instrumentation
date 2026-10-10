/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ApiGatewayProxyAttributesExtractorTest {

  @Test
  void redactsSensitiveQueryParameters() {
    Attributes attributes = attributes(HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q=value&sig=REDACTED");
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q=value&sig=REDACTED");
  }

  @Test
  void redactsConfiguredQueryParameters() {
    Attributes attributes = attributes(singleton("q"));
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q=REDACTED&sig=secret");
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q=REDACTED&sig=secret");
  }

  @Test
  void encodesQueryParameters() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q name", "a b&c=d?e");
    query.put("sig", "secret");

    Attributes attributes = attributes(query, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q+name=a+b%26c%3Dd%3Fe&sig=REDACTED");
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q+name=a+b%26c%3Dd%3Fe&sig=REDACTED");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void omitsAbsentQuery(Map<String, String> query) {
    Attributes attributes = attributes(query, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_QUERY)).isNull();
    assertThat(attributes.get(URL_FULL)).isEqualTo("https://localhost:123/hello");
  }

  private static Attributes attributes(Set<String> sensitiveQueryParameters) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q", "value");
    query.put("sig", "secret");
    return attributes(query, sensitiveQueryParameters);
  }

  private static Attributes attributes(
      Map<String, String> query, Set<String> sensitiveQueryParameters) {
    ApiGatewayProxyAttributesExtractor extractor =
        new ApiGatewayProxyAttributesExtractor(
            HttpConstants.KNOWN_METHODS, sensitiveQueryParameters);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Host", "localhost:123");
    headers.put("X-Forwarded-Proto", "https");

    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/hello")
            .withQueryStringParameters(query)
            .withHeaders(headers);

    AttributesBuilder attributes = Attributes.builder();
    extractor.onRequest(attributes, request);
    return attributes.build();
  }
}
