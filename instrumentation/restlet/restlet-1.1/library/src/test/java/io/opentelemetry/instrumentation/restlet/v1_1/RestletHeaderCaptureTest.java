/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.restlet.Filter;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

class RestletHeaderCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedSettersMatchHeaderNamesLiterally() {
    Filter filter =
        RestletTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createFilter("/test");
    filter.setNext(new Restlet() {});

    Request request = new Request(Method.GET, "http://localhost/test");
    request.setOriginalRef(new Reference("http://localhost/test"));
    // Authorization is present so that treating "*" as a glob would capture it
    request
        .getAttributes()
        .put("org.restlet.http.headers", headers("X-Test-Request", "Authorization"));
    Response response = new Response(request);
    response.setStatus(Status.SUCCESS_OK);
    response.getAttributes().put("org.restlet.http.headers", headers("X-Test-Response"));

    filter.handle(request, response);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces.get(0).get(0).getAttributes().asMap().keySet())
        .extracting(AttributeKey::getKey)
        .noneMatch(key -> key.startsWith("http.request.header."))
        .noneMatch(key -> key.startsWith("http.response.header."));
  }

  private static Form headers(String... names) {
    Form headers = new Form();
    for (String name : names) {
      headers.add(name, name + "-value");
    }
    return headers;
  }
}
