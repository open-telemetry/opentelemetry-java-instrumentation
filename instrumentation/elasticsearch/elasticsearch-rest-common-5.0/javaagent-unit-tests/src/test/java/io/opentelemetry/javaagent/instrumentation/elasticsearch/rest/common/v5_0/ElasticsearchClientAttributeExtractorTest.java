/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.Set;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicRequestLine;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.Test;

class ElasticsearchClientAttributeExtractorTest {

  @Test
  void redactsSensitiveQueryParameters() {
    assertThat(urlFull(HttpConstants.SENSITIVE_QUERY_PARAMETERS))
        .isEqualTo("http://localhost:9200/_search?q=value&sig=REDACTED");
  }

  @Test
  void redactsConfiguredQueryParameters() {
    assertThat(urlFull(singleton("q")))
        .isEqualTo("http://localhost:9200/_search?q=REDACTED&sig=secret");
  }

  private static String urlFull(Set<String> sensitiveQueryParameters) {
    ElasticsearchClientAttributeExtractor extractor =
        new ElasticsearchClientAttributeExtractor(
            HttpConstants.KNOWN_METHODS, sensitiveQueryParameters);

    Response response = mock(Response.class);
    when(response.getHost()).thenReturn(new HttpHost("localhost", 9200, "http"));
    when(response.getRequestLine())
        .thenReturn(
            new BasicRequestLine("GET", "/_search?q=value&sig=secret", HttpVersion.HTTP_1_1));

    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(
        attributes,
        Context.root(),
        ElasticsearchRestRequest.create("GET", "/_search"),
        response,
        null);
    return attributes.build().get(URL_FULL);
  }
}
