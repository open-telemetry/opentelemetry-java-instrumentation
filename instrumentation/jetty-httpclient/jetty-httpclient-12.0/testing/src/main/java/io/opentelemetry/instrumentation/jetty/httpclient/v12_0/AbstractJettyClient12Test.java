/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.FutureResponseListener;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractJettyClient12Test extends AbstractHttpClientTest<Request> {

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory);

  protected HttpClient client = createStandardClient();
  protected HttpClient httpsClient;

  @BeforeEach
  public void before() throws Exception {
    client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis());
    client.start();

    SslContextFactory.Client tlsCtx = new SslContextFactory.Client();
    httpsClient = createHttpsClient(tlsCtx);
    httpsClient.setFollowRedirects(false);
    httpsClient.start();
  }

  @AfterEach
  public void after() throws Exception {
    client.stop();
    httpsClient.stop();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // disable redirect tests
    optionsBuilder.disableTestRedirects();
    // jetty 12 does not support to reuse request
    // use request.send() twice will block the program infinitely
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.spanEndsAfterBody();
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClient theClient = Objects.equals(uri.getScheme(), "https") ? httpsClient : client;

    Request request = theClient.newRequest(uri);
    request.agent("Jetty");

    request.method(method);
    if (uri.toString().contains("/read-timeout")) {
      request.timeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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
  void callbacksCalled() throws InterruptedException, ExecutionException {
    URI uri = resolveAddress("/success");
    Request request = client.newRequest(uri).method("GET");
    FutureResponseListener responseListener =
        new FutureResponseListener(request) {
          @Override
          public void onHeaders(Response response) {
            testing.runWithSpan("onHeaders", () -> super.onHeaders(response));
          }

          @Override
          public void onSuccess(Response response) {
            testing.runWithSpan("onSuccess", () -> super.onSuccess(response));
          }

          @Override
          public void onComplete(Result result) {
            testing.runWithSpan("onComplete", () -> super.onComplete(result));
          }
        };
    testing.runWithSpan("parent", () -> request.send(responseListener));
    Response response = responseListener.get();

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

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    volatile Throwable failure;

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
