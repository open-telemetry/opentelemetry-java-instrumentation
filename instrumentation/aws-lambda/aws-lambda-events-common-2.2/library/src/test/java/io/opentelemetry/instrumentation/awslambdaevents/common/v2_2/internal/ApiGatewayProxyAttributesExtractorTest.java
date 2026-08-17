/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
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

  private static String urlFull(Set<String> sensitiveQueryParameters) {
    ApiGatewayProxyAttributesExtractor extractor =
        new ApiGatewayProxyAttributesExtractor(
            HttpConstants.KNOWN_METHODS, sensitiveQueryParameters);

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

    AttributesBuilder attributes = Attributes.builder();
    extractor.onRequest(attributes, request);
    return attributes.build().get(URL_FULL);
  }
}
