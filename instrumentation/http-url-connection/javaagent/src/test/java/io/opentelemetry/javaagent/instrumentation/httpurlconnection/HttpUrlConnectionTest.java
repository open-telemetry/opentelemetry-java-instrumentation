/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.StreamUtils.readLines;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setMaxRedirects(20);

    // HttpURLConnection can't be reused
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.disableTestCallback();
    optionsBuilder.disableTestNonStandardHttpMethod();
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

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, url.getPort()),
                equalTo(URL_FULL, url.toString()),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, STATUS)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(attributes),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(attributes),
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

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, url.getPort()),
                equalTo(URL_FULL, url.toString()),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, STATUS)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(attributes),
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

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, url.getPort()),
                equalTo(URL_FULL, url.toString()),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, STATUS)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("POST")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(attributes),
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

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, url.getPort()),
                equalTo(URL_FULL, url.toString()),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, STATUS)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasNoParent(),
                span ->
                    span.hasName("POST")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(attributes),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void traceRequestWithConnectionFailure(String scheme) {
    String uri = scheme + "://localhost:" + PortUtils.UNUSABLE_PORT;

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "someTrace",
                    () -> {
                      URL url = new URI(uri).toURL();
                      URLConnection connection = url.openConnection();
                      connection.setConnectTimeout(10000);
                      connection.setReadTimeout(10000);
                      assertThat(Span.current().getSpanContext().isValid()).isTrue();
                      connection.getInputStream();
                    }));

    List<AttributeAssertion> attributes =
        new ArrayList<>(
            Arrays.asList(
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, PortUtils.UNUSABLE_PORT),
                equalTo(URL_FULL, uri),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(ERROR_TYPE, "java.net.ConnectException")));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("someTrace")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(attributes)));
  }
}
