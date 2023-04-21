/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.ClassInfo;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    GenericUrl genericUrl = new GenericUrl(uri);

    HttpRequest request = requestFactory.buildRequest(method, genericUrl, null);
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
  public int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    HttpResponse response = sendRequest(request);
    // read request body to avoid broken pipe errors on the server side
    response.parseAsString();
    return response.getStatusCode();
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            satisfies(SemanticAttributes.NET_PEER_PORT, port -> port.isPositive()),
                            equalTo(SemanticAttributes.HTTP_URL, uri.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 500),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                length -> length.isPositive())),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    optionsBuilder.disableTestCallback();

    optionsBuilder.enableTestReadTimeout();

    // Circular redirects don't throw an exception with Google Http Client
    optionsBuilder.disableTestCircularRedirects();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(stringKey("net.protocol.name"));
          attributes.remove(stringKey("net.protocol.version"));
          return attributes;
        });
  }
}
