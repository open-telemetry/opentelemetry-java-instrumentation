/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Test;

class ApacheHttpClientCapturedHeadersTest {

  @Test
  void capturesHeadersMatchedByWildcardSelector() {
    HttpGet httpRequest = new HttpGet("/test");
    httpRequest.setHeader("X-Test-Request", "request-value");
    httpRequest.setHeader("X-Test-Excluded", "excluded-value");
    httpRequest.setHeader("Other-Request", "other-value");
    ApacheHttpClientRequest request =
        new ApacheHttpClientRequest(new HttpHost("localhost", 8080), httpRequest);

    HttpResponse response =
        new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
    response.setHeader("X-Test-Response", "response-value");
    response.setHeader("Other-Response", "other-value");

    // the selector patterns use different casing than the headers to verify that HTTP header
    // matching is case-insensitive
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded("x-test-*").setExcluded("*-EXCLUDED").build();
    AttributesExtractor<ApacheHttpClientRequest, HttpResponse> extractor =
        HttpClientAttributesExtractor.builder(new ApacheHttpClientHttpAttributesGetter())
            .setRequestHeaders(selector)
            .setResponseHeaders(selector)
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, response, null);

    Attributes result = attributes.build();
    assertThat(result.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("request-value"));
    assertThat(result.get(stringArrayKey("http.request.header.x-test-excluded"))).isNull();
    assertThat(result.get(stringArrayKey("http.request.header.other-request"))).isNull();
    assertThat(result.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("response-value"));
    assertThat(result.get(stringArrayKey("http.response.header.other-response"))).isNull();
  }

  @Test
  void capturesHeadersMatchedByExcludeOnlySelector() {
    HttpGet httpRequest = new HttpGet("/test");
    httpRequest.setHeader("X-Test-Request", "request-value");
    httpRequest.setHeader("X-Test-Excluded", "excluded-value");
    ApacheHttpClientRequest request =
        new ApacheHttpClientRequest(new HttpHost("localhost", 8080), httpRequest);

    AttributesExtractor<ApacheHttpClientRequest, HttpResponse> extractor =
        HttpClientAttributesExtractor.builder(new ApacheHttpClientHttpAttributesGetter())
            .setRequestHeaders(IncludeExclude.builder().setExcluded("*-excluded").build())
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    Attributes result = attributes.build();
    assertThat(result.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("request-value"));
    assertThat(result.get(stringArrayKey("http.request.header.x-test-excluded"))).isNull();
  }
}
