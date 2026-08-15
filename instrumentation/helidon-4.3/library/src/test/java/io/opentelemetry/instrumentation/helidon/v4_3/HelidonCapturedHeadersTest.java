/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.helidon.http.HeaderNames;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import org.junit.jupiter.api.Test;

class HelidonCapturedHeadersTest {

  @Test
  void capturesHeadersMatchedByWildcardSelector() {
    WritableHeaders<?> requestHeaders = WritableHeaders.create();
    requestHeaders.add(HeaderNames.create("X-Test-Request"), "request-value");
    requestHeaders.add(HeaderNames.create("X-Test-Excluded"), "excluded-value");
    requestHeaders.add(HeaderNames.create("Other-Request"), "other-value");
    ServerRequest request = mock(ServerRequest.class, RETURNS_DEEP_STUBS);
    when(request.headers()).thenReturn(ServerRequestHeaders.create(requestHeaders));

    ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
    responseHeaders.add(HeaderNames.create("X-Test-Response"), "response-value");
    responseHeaders.add(HeaderNames.create("Other-Response"), "other-value");
    ServerResponse response = mock(ServerResponse.class, RETURNS_DEEP_STUBS);
    when(response.headers()).thenReturn(responseHeaders);

    // the selector patterns use different casing than the headers to verify that HTTP header
    // matching is case-insensitive
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded("x-test-*").setExcluded("*-EXCLUDED").build();
    AttributesExtractor<ServerRequest, ServerResponse> extractor =
        HttpServerAttributesExtractor.builder(new HelidonAttributesGetter())
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
    WritableHeaders<?> requestHeaders = WritableHeaders.create();
    requestHeaders.add(HeaderNames.create("X-Test-Request"), "request-value");
    requestHeaders.add(HeaderNames.create("X-Test-Excluded"), "excluded-value");
    requestHeaders.add(HeaderNames.create("Other-Request"), "other-value");
    ServerRequest request = mock(ServerRequest.class, RETURNS_DEEP_STUBS);
    when(request.headers()).thenReturn(ServerRequestHeaders.create(requestHeaders));

    AttributesExtractor<ServerRequest, ServerResponse> extractor =
        HttpServerAttributesExtractor.builder(new HelidonAttributesGetter())
            .setRequestHeaders(IncludeExclude.builder().setExcluded("*-excluded").build())
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    Attributes result = attributes.build();
    assertThat(result.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("request-value"));
    assertThat(result.get(stringArrayKey("http.request.header.other-request")))
        .isEqualTo(singletonList("other-value"));
    assertThat(result.get(stringArrayKey("http.request.header.x-test-excluded"))).isNull();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedExactNameSetterDoesNotTreatStarAsWildcard() {
    WritableHeaders<?> requestHeaders = WritableHeaders.create();
    requestHeaders.add(HeaderNames.create("X-Test-Request"), "request-value");
    requestHeaders.add(HeaderNames.create("Authorization"), "secret");
    ServerRequest request = mock(ServerRequest.class, RETURNS_DEEP_STUBS);
    when(request.headers()).thenReturn(ServerRequestHeaders.create(requestHeaders));

    ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
    responseHeaders.add(HeaderNames.create("X-Test-Response"), "response-value");
    ServerResponse response = mock(ServerResponse.class, RETURNS_DEEP_STUBS);
    when(response.headers()).thenReturn(responseHeaders);

    // implementing header name enumeration must not turn the deprecated exact-name setters into
    // wildcard matching, since "*" is a legal header name character and capturing every header
    // would expose credentials
    AttributesExtractor<ServerRequest, ServerResponse> extractor =
        HttpServerAttributesExtractor.builder(new HelidonAttributesGetter())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, response, null);

    Attributes result = attributes.build();
    assertThat(result.get(stringArrayKey("http.request.header.x-test-request"))).isNull();
    assertThat(result.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(result.get(stringArrayKey("http.response.header.x-test-response"))).isNull();
  }
}
