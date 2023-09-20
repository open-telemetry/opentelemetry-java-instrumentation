/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

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
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.AbstractLongAssert;
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
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void errorTracesWhenExceptionIsNotThrown() throws Exception {
    URI uri = resolveAddress("/error");

    HttpRequest request = buildRequest("GET", uri, Collections.emptyMap());
    int responseCode = sendRequest(request, "GET", uri, Collections.emptyMap());

    assertThat(responseCode).isEqualTo(500);

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(getAttributeKey(SemanticAttributes.NET_PEER_NAME), "localhost"),
                satisfies(
                    getAttributeKey(SemanticAttributes.NET_PEER_PORT),
                    AbstractLongAssert::isPositive),
                equalTo(getAttributeKey(SemanticAttributes.HTTP_URL), uri.toString()),
                equalTo(getAttributeKey(SemanticAttributes.HTTP_METHOD), "GET"),
                equalTo(getAttributeKey(SemanticAttributes.HTTP_STATUS_CODE), 500),
                satisfies(
                    getAttributeKey(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH),
                    AbstractLongAssert::isPositive)));
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(equalTo(HttpAttributes.ERROR_TYPE, "500"));
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(attributes),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
  }

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    optionsBuilder.disableTestCallback();

    // Circular redirects don't throw an exception with Google Http Client
    optionsBuilder.disableTestCircularRedirects();

    // can only use supported method
    optionsBuilder.disableTestNonStandardHttpMethod();

    if (SemconvStability.emitOldHttpSemconv()) {
      optionsBuilder.setHttpAttributes(
          uri -> {
            Set<AttributeKey<?>> attributes =
                new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_NAME);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_VERSION);
            return attributes;
          });
    }
  }
}
