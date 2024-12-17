/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class JaxRsClientTest extends AbstractHttpClientTest<Invocation.Builder> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  protected static final List<String> BODY_METHODS = asList("POST", "PUT");
  protected static final int CONNECT_TIMEOUT_MS = 5000;
  protected static final int READ_TIMEOUT_MS = 2000;

  static Stream<Arguments> preparedPathStream() {
    return Stream.of(Arguments.of("/client-error", 400), Arguments.of("/error", 500));
  }

  @Override
  public Invocation.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    return internalBuildRequest(uri, headers);
  }

  abstract ClientBuilder builder();

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setTestRedirects(false);
    optionsBuilder.setTestNonStandardHttpMethod(false);
  }

  private Invocation.Builder internalBuildRequest(URI uri, Map<String, String> headers) {
    Client client = builder().build();
    WebTarget service = client.target(uri);
    Invocation.Builder requestBuilder = service.request(MediaType.TEXT_PLAIN);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      requestBuilder.header(entry.getKey(), entry.getValue());
    }
    return requestBuilder;
  }

  @Override
  public int sendRequest(
      Invocation.Builder request, String method, URI uri, Map<String, String> headers) {
    try {
      Entity<String> body = BODY_METHODS.contains(method) ? Entity.text("") : null;
      Response response = request.build(method, body).invoke();
      // read response body to avoid broken pipe errors on the server side
      response.readEntity(String.class);
      response.close();
      return response.getStatus();
    } catch (ProcessingException exception) {
      throw exception;
    }
  }

  @Override
  public void sendRequestWithCallback(
      Invocation.Builder request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    Entity<String> body = BODY_METHODS.contains(method) ? Entity.text("") : null;

    request
        .async()
        .method(
            method,
            body,
            new InvocationCallback<Response>() {
              @Override
              public void completed(Response response) {
                // read response body
                response.readEntity(String.class);
                requestResult.complete(response.getStatus());
              }

              @Override
              public void failed(Throwable throwable) {
                if (throwable instanceof ProcessingException) {
                  throwable = throwable.getCause();
                }
                requestResult.complete(throwable);
              }
            });
  }

  @ParameterizedTest
  @MethodSource("preparedPathStream")
  void testError(String path, int statusCode) throws Throwable {
    String method = "GET";
    URI uri = resolveAddress(path);
    int actualStatusCode =
        sendRequest(
            buildRequest(method, uri, Collections.emptyMap()), method, uri, Collections.emptyMap());
    assertThat(actualStatusCode).isEqualTo(statusCode);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(method)
                        .hasKind(CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfying(
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            satisfies(ServerAttributes.SERVER_PORT, val -> val.isIn(null, 443)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                val -> val.isIn(null, "127.0.0.1")),
                            equalTo(UrlAttributes.URL_FULL, uri.getHost()),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, method),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode),
                            equalTo(ErrorAttributes.ERROR_TYPE, "$statusCode")),
                span -> span.hasParent(trace.getSpan(0)).hasName(method)));
  }
}
