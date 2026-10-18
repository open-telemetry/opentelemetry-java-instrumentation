/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractJettyClient12Test extends AbstractHttpClientTest<Request> {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private HttpClient client;
  private HttpClient httpsClient;

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory);

  @BeforeEach
  void before() throws Exception {
    client = createStandardClient();
    client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis());
    client.start();
    cleanup.deferCleanup(client::stop);

    SslContextFactory.Client tlsCtx = new SslContextFactory.Client();
    httpsClient = createHttpsClient(tlsCtx);
    httpsClient.setFollowRedirects(false);
    httpsClient.start();
    cleanup.deferCleanup(httpsClient::stop);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // disable redirect tests
    optionsBuilder.disableTestRedirects();
    // Jetty 12 does not support reusing requests.
    // Calling request.send() twice blocks indefinitely.
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.spanEndsAfterBody();
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClient theClient = "https".equalsIgnoreCase(uri.getScheme()) ? httpsClient : client;

    Request request = theClient.newRequest(uri);
    request.agent("Jetty");

    request.method(method);
    if (uri.getPath().endsWith("/read-timeout")) {
      request.timeout(READ_TIMEOUT.toMillis(), MILLISECONDS);
    }

    return request;
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    ContentResponse response = request.send();

    return response.getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    JettyClientListener clientListener = new JettyClientListener();

    request.onRequestFailure(clientListener);
    request.onResponseFailure(clientListener);
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    request.send(
        result -> {
          if (clientListener.failure != null) {
            requestResult.complete(clientListener.failure);
            return;
          }

          requestResult.complete(result.getResponse().getStatus());
        });
  }

  @Test
  void responseBodyTruncatedAfterSuccessfulHeaders()
      throws ExecutionException, IOException, InterruptedException, TimeoutException {
    URI uri;
    Result result;
    try (ServerSocket server =
        new ServerSocket(PortUtils.findOpenPort(), 1, InetAddress.getAllByName("127.0.0.1")[0])) {
      server.setSoTimeout(10_000);
      uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/truncated");
      CompletableFuture<Result> responseFuture = new CompletableFuture<>();
      client.newRequest(uri).timeout(10, SECONDS).send(responseFuture::complete);

      try (Socket socket = server.accept()) {
        socket.setSoTimeout(10_000);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {}
        // Close before the declared body is complete, after sending valid successful headers.
        socket
            .getOutputStream()
            .write(
                "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\npartial"
                    .getBytes(US_ASCII));
        socket.getOutputStream().flush();
      }
      result = responseFuture.get(10, SECONDS);
    }

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponseFailure()).isInstanceOf(EOFException.class);
    String errorType = result.getResponseFailure().getClass().getName();
    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET")
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasAttributesSatisfyingExactly(
                          equalTo(HTTP_REQUEST_METHOD, "GET"),
                          equalTo(URL_FULL, uri.toString()),
                          equalTo(SERVER_ADDRESS, "127.0.0.1"),
                          equalTo(SERVER_PORT, uri.getPort()),
                          equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                          equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                          equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                          equalTo(ERROR_TYPE, errorType)));
        });
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metric ->
            metric
                .hasName("http.client.request.duration")
                .hasUnit("s")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasSumGreaterThan(0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                                        equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                        equalTo(SERVER_PORT, uri.getPort()),
                                        equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                        equalTo(ERROR_TYPE, errorType)))));
  }

  @Test
  void callbacksCalled() {
    URI uri = resolveAddress("/success");
    Request request = client.newRequest(uri).method("GET");

    CompletableFuture<Response> responseFuture = new CompletableFuture<>();
    TracingResponseListener responseListener = new TracingResponseListener(responseFuture);

    testing.runWithSpan("parent", () -> request.send(responseListener));
    Response response = responseFuture.join();

    assertThat(response.getStatus()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("onHeaders")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("onSuccess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("onComplete")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private class TracingResponseListener
      implements Response.HeadersListener, Response.SuccessListener, Response.CompleteListener {

    private final CompletableFuture<Response> responseFuture;

    TracingResponseListener(CompletableFuture<Response> responseFuture) {
      this.responseFuture = responseFuture;
    }

    @Override
    public void onHeaders(Response response) {
      testing.runWithSpan("onHeaders", () -> {});
    }

    @Override
    public void onSuccess(Response response) {
      testing.runWithSpan("onSuccess", () -> {});
    }

    @Override
    public void onComplete(Result result) {
      testing.runWithSpan(
          "onComplete",
          () -> {
            if (result.isSucceeded()) {
              responseFuture.complete(result.getResponse());
            } else {
              responseFuture.completeExceptionally(result.getFailure());
            }
          });
    }
  }

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    private volatile Throwable failure;

    @Override
    public void onFailure(Request request, Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onFailure(Response response, Throwable failure) {
      this.failure = failure;
    }
  }
}
