/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
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

class ApiGatewayProxyAttributesExtractorTest {

  @Test
  void redactsSensitiveQueryParameters() {
    assertThat(urlFull(HttpConstants.SENSITIVE_QUERY_PARAMETERS))
        .isEqualTo("https://localhost:123/hello?q=value&sig=REDACTED");
  }

  @Test
  void redactsConfiguredQueryParameters() {
    assertThat(urlFull(singleton("q")))
        .isEqualTo("https://localhost:123/hello?q=REDACTED&sig=secret");
  }

  @Test
  void extractsHttpAttributes() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Forwarded-Proto", "https");
    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withPath("/hello/world")
            .withResource("/hello/{name}")
            .withHeaders(headers);

    Attributes attributes = extract(request);

    assertThat(attributes.get(URL_PATH)).isEqualTo("/hello/world");
    assertThat(attributes.get(URL_SCHEME)).isEqualTo("https");
    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/hello/{name}");
  }

  @Test
  void omitsUnavailableHttpAttributes() {
    Attributes attributes = extract(new APIGatewayProxyRequestEvent());

    assertThat(attributes.get(URL_PATH)).isNull();
    assertThat(attributes.get(URL_SCHEME)).isNull();
    assertThat(attributes.get(HTTP_ROUTE)).isNull();
  }

  private static String urlFull(Set<String> sensitiveQueryParameters) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q", "value");
    query.put("sig", "secret");

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Host", "localhost:123");
    headers.put("X-Forwarded-Proto", "https");

    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/hello")
            .withQueryStringParameters(query)
            .withHeaders(headers);

    return extract(request, sensitiveQueryParameters).get(URL_FULL);
  }

  private static Attributes extract(APIGatewayProxyRequestEvent request) {
    return extract(request, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
  }

  private static Attributes extract(
      APIGatewayProxyRequestEvent request, Set<String> sensitiveQueryParameters) {
    ApiGatewayProxyAttributesExtractor extractor =
        new ApiGatewayProxyAttributesExtractor(
            HttpConstants.KNOWN_METHODS, sensitiveQueryParameters);
    AttributesBuilder attributes = Attributes.builder();
    extractor.onRequest(attributes, request);
    return attributes.build();
  }
}
