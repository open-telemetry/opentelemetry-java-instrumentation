/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.StreamUtils.readLines;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlConnectionTest extends AbstractHttpClientTest<HttpURLConnection> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  static final List<String> RESPONSE = Collections.singletonList("Hello.");
  static final int STATUS = 200;

  @Override
  public HttpURLConnection buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    return (HttpURLConnection) uri.toURL().openConnection();
  }

  @Override
  public int sendRequest(
      HttpURLConnection connection, String method, URI uri, Map<String, String> headers)
      throws Exception {
    if (uri.toString().contains("/read-timeout")) {
      connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
    }
    try {
      connection.setRequestMethod(method);
      headers.forEach(connection::setRequestProperty);
      connection.setRequestProperty("Connection", "close");
      connection.setUseCaches(true);
      connection.setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis());
      Span parentSpan = Span.current();
      InputStream stream = connection.getInputStream();
      assertThat(Span.current()).isEqualTo(parentSpan);
      stream.close();
      return connection.getResponseCode();
    } finally {
      connection.disconnect();
    }
  }

  @Override
  public int maxRedirects() {
    return 20;
  }

  @Override
  public Integer responseCodeOnRedirectError() {
    return 302;
  }

  @Override
  public boolean testReusedRequest() {
    // HttpURLConnection can't be reused
    return false;
  }

  @Override
  public boolean testCallback() {
    return false;
  }

  @Override
  public boolean testReadTimeout() {
    return true;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void traceRequest(boolean useCache) throws IOException {
    URL url = resolveAddress("/success").toURL();

    testing.runWithSpan(
        "someTrace",
        () -> {
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setUseCaches(useCache);
          assertThat(Span.current().getSpanContext().isValid()).isTrue();
          InputStream stream = connection.getInputStream();
          List<String> lines = readLines(stream);
          stream.close();
          assertThat(connection.getResponseCode()).isEqualTo(STATUS);
          assertThat(lines).isEqualTo(RESPONSE);

          // call again to ensure the cycling is ok
          connection = (HttpURLConnection) url.openConnection();
          connection.setUseCaches(useCache);
          assertThat(Span.current().getSpanContext().isValid()).isTrue();
          // call before input stream to test alternate behavior
          assertThat(connection.getResponseCode()).isEqualTo(STATUS);
          connection.getInputStream();
          stream = connection.getInputStream(); // one more to ensure state is working
          lines = readLines(stream);
          stream.close();
          assertThat(lines).isEqualTo(RESPONSE);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, url.getPort()),
                            equalTo(SemanticAttributes.HTTP_URL, url.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, STATUS),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, url.getPort()),
                            equalTo(SemanticAttributes.HTTP_URL, url.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, STATUS),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(3))));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
  public void testBrokenApiUsage() throws IOException {
    URL url = resolveAddress("/success").toURL();
    HttpURLConnection connection =
        testing.runWithSpan(
            "someTrace",
            () -> {
              HttpURLConnection con = (HttpURLConnection) url.openConnection();
              con.setRequestProperty("Connection", "close");
              assertThat(Span.current().getSpanContext().isValid()).isTrue();
              assertThat(con.getResponseCode()).isEqualTo(STATUS);
              return con;
            });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, url.getPort()),
                            equalTo(SemanticAttributes.HTTP_URL, url.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, STATUS),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));

    connection.disconnect();
  }

  @Test
  public void testPostRequest() throws IOException {
    URL url = resolveAddress("/success").toURL();
    testing.runWithSpan(
        "someTrace",
        () -> {
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setRequestMethod("POST");

          String urlParameters = "q=ASDF&w=&e=&r=12345&t=";

          // Send post request
          connection.setDoOutput(true);
          DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
          wr.writeBytes(urlParameters);
          wr.flush();
          wr.close();

          assertThat(connection.getResponseCode()).isEqualTo(STATUS);

          InputStream stream = connection.getInputStream();
          List<String> lines = readLines(stream);
          stream.close();
          assertThat(lines).isEqualTo(RESPONSE);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("POST")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, url.getPort()),
                            equalTo(SemanticAttributes.HTTP_URL, url.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, STATUS),
                            satisfies(
                                SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));
  }

  @Test
  public void getOutputStreamShouldTransformGetIntoPost() throws IOException {
    URL url = resolveAddress("/success").toURL();
    testing.runWithSpan(
        "someTrace",
        () -> {
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();

          assertThat(connection.getClass().getName())
              .isEqualTo("sun.net.www.protocol.http.HttpURLConnection");

          connection.setRequestMethod("GET");

          String urlParameters = "q=ASDF&w=&e=&r=12345&t=";

          // Send POST request
          connection.setDoOutput(true);
          DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
          wr.writeBytes(urlParameters);
          wr.flush();
          wr.close();

          assertThat(connection.getResponseCode()).isEqualTo(STATUS);

          InputStream stream = connection.getInputStream();
          List<String> lines = readLines(stream);
          stream.close();
          assertThat(lines).isEqualTo(RESPONSE);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("POST")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, url.getPort()),
                            equalTo(SemanticAttributes.HTTP_URL, url.toString()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, STATUS),
                            satisfies(
                                SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));
  }
}
