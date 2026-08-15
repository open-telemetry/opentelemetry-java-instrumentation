/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import org.junit.jupiter.api.Test;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.Method;
import org.restlet.data.Reference;

class RestletHttpAttributesGetterTest {

  @Test
  void capturesHeadersMatchingWildcardSelector() {
    Request request = newRequest();
    request.getAttributes().put("org.restlet.http.headers", headers("X-Test-Request", "X-Secret"));
    Response response = new Response(request);
    response.getAttributes().put("org.restlet.http.headers", headers("X-Test-Response"));

    AttributesExtractor<Request, Response> extractor =
        HttpServerAttributesExtractor.<Request, Response>builder(new RestletHttpAttributesGetter())
            .setRequestHeaders(
                IncludeExclude.builder()
                    .setIncluded(singletonList("x-*"))
                    .setExcluded(singletonList("x-secret"))
                    .build())
            .setResponseHeaders(IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, response, null);

    assertThat(attributes.build().asMap())
        .contains(
            entry(
                stringArrayKey("http.request.header.x-test-request"),
                singletonList("X-Test-Request-value")),
            entry(
                stringArrayKey("http.response.header.x-test-response"),
                singletonList("X-Test-Response-value")))
        .doesNotContainKey(stringArrayKey("http.request.header.x-secret"));
  }

  @Test
  void capturesAllHeadersWithExcludeOnlySelector() {
    Request request = newRequest();
    request.getAttributes().put("org.restlet.http.headers", headers("X-One", "X-Two"));

    AttributesExtractor<Request, Response> extractor =
        HttpServerAttributesExtractor.<Request, Response>builder(new RestletHttpAttributesGetter())
            .setRequestHeaders(IncludeExclude.builder().setExcluded(singletonList("x-two")).build())
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build().asMap())
        .contains(entry(stringArrayKey("http.request.header.x-one"), singletonList("X-One-value")))
        .doesNotContainKey(stringArrayKey("http.request.header.x-two"));
  }

  private static Request newRequest() {
    Request request = new Request(Method.GET, "http://localhost/test");
    request.setOriginalRef(new Reference("http://localhost/test"));
    return request;
  }

  private static Form headers(String... names) {
    Form headers = new Form();
    for (String name : names) {
      headers.add(name, name + "-value");
    }
    return headers;
  }
}
