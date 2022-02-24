/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.ClassInfo;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractGoogleHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private HttpRequestFactory requestFactory;

  @BeforeAll
  void setUp() {
    requestFactory = new NetHttpTransport().createRequestFactory();
  }

  @Override
  protected final HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    GenericUrl genericUrl = new GenericUrl(uri);

    HttpRequest request;
    try {
      request = requestFactory.buildRequest(method, genericUrl, null);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    request.setConnectTimeout((int) connectTimeout().toMillis());
    if (uri.toString().contains("/read-timeout")) {
      request.setReadTimeout((int) readTimeout().toMillis());
    }

    // GenericData::putAll method converts all known http headers to List<String>
    // and lowercase all other headers
    ClassInfo ci = request.getHeaders().getClassInfo();
    headers.forEach(
        (name, value) ->
            request
                .getHeaders()
                .put(name, ci.getFieldInfo(name) != null ? value : value.toLowerCase(Locale.ROOT)));

    request.setThrowExceptionOnExecuteError(false);
    return request;
  }

  @Override
  protected final int sendRequest(
      HttpRequest request, String method, URI uri, Map<String, String> headers) throws Exception {
    return sendRequest(request).getStatusCode();
  }

  protected abstract HttpResponse sendRequest(HttpRequest request) throws Exception;

  @Test
  void errorTracesWhenExceptionIsNotThrown() throws Exception {
    URI uri = resolveAddress("/error");

    HttpRequest request = buildRequest("GET", uri, Collections.emptyMap());
    int responseCode = sendRequest(request, "GET", uri, Collections.emptyMap());

    assertThat(responseCode).isEqualTo(500);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .hasSize(7)
                                    .containsEntry(
                                        SemanticAttributes.NET_TRANSPORT,
                                        SemanticAttributes.NetTransportValues.IP_TCP)
                                    .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost")
                                    .hasEntrySatisfying(
                                        SemanticAttributes.NET_PEER_PORT,
                                        port -> assertThat(port).isPositive())
                                    .containsEntry(SemanticAttributes.HTTP_URL, uri.toString())
                                    .containsEntry(SemanticAttributes.HTTP_METHOD, "GET")
                                    .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 500)
                                    .containsEntry(
                                        SemanticAttributes.HTTP_FLAVOR,
                                        SemanticAttributes.HttpFlavorValues.HTTP_1_1)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
  }

  @Override
  protected void configure(HttpClientTestOptions options) {
    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    options.disableTestCallback();

    options.enableTestReadTimeout();

    // Circular redirects don't throw an exception with Google Http Client
    options.disableTestCircularRedirects();
  }
}
