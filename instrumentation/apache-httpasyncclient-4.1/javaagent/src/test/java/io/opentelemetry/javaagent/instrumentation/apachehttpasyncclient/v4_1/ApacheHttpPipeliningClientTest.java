/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpPipeliningClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ApacheHttpPipeliningClientTest extends AbstractHttpClientTest<HttpUriRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final RequestConfig REQUEST_CONFIG =
      RequestConfig.custom().setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis()).build();

  private static final RequestConfig READ_TIMEOUT_REQUEST_CONFIG =
      RequestConfig.copy(REQUEST_CONFIG).setSocketTimeout((int) READ_TIMEOUT.toMillis()).build();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private CloseableHttpPipeliningClient client;

  @BeforeEach
  void setUp() {
    client = HttpAsyncClients.createPipelining();
    cleanup.deferCleanup(client);
    client.start();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder
        .disableTestRedirects()
        .disableTestConnectionFailure()
        .disableTestRemoteConnection()
        .spanEndsAfterBody();
  }

  @Override
  public HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpUriRequest request = new HttpUriRequest(method, URI.create(fullPathFromUri(uri)));
    request.addHeader("user-agent", "httpasyncclient");
    headers.forEach((key, value) -> request.setHeader(new BasicHeader(key, value)));
    return request;
  }

  @Override
  public int sendRequest(
      HttpUriRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    List<HttpRequest> requests = singletonList(request);
    List<HttpResponse> responses = client.execute(target(uri), requests, context(uri), null).get();
    return getResponseCode(responses.get(0));
  }

  @Override
  public void sendRequestWithCallback(
      HttpUriRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult result) {
    List<HttpRequest> requests = singletonList(request);
    client.execute(target(uri), requests, context(uri), new ResponseCallback(result));
  }

  @Test
  void pipelinedRequestsCreateIndependentSpansAndPropagateCallbackContext() throws Exception {
    URI successUri = resolveAddress("/success");
    URI errorUri = resolveAddress("/error");
    HttpHost target = target(successUri);

    HttpGet successRequest = new HttpGet(successUri.getRawPath());
    successRequest.setHeader("test-request-id", "1");
    HttpGet errorRequest = new HttpGet(errorUri.getRawPath());
    errorRequest.setHeader("test-request-id", "2");
    List<HttpRequest> requests = asList(successRequest, errorRequest);

    CountDownLatch callbackDone = new CountDownLatch(1);
    AtomicReference<Throwable> callbackFailure = new AtomicReference<>();

    FutureCallback<List<HttpResponse>> callback =
        new FutureCallback<List<HttpResponse>>() {
          @Override
          public void completed(List<HttpResponse> ignored) {
            try {
              testing.runWithSpan("callback", () -> {});
            } catch (Throwable t) {
              callbackFailure.set(t);
            } finally {
              callbackDone.countDown();
            }
          }

          @Override
          public void failed(Exception e) {
            callbackFailure.set(e);
            callbackDone.countDown();
          }

          @Override
          public void cancelled() {
            callbackFailure.set(new CancellationException());
            callbackDone.countDown();
          }
        };

    List<HttpResponse> responses =
        testing.runWithSpan("parent", () -> client.execute(target, requests, null, callback).get());

    assertThat(callbackDone.await(10, SECONDS)).isTrue();
    assertThat(callbackFailure.get()).isNull();
    assertThat(responses)
        .extracting(response -> getResponseCode(response))
        .containsExactly(200, 500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    assertClientSpan(span, successUri, "GET", 200, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset()),
                span ->
                    assertClientSpan(span, errorUri, "GET", 500, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 1)),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 2)),
                span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void pipelinedFailureEndsAllStartedRequestSpansWithError() throws Exception {
    URI firstUri = resolveAddress("/long-request");
    URI secondUri = resolveAddress("/success");
    HttpHost target = target(firstUri);

    HttpGet firstRequest = new HttpGet(firstUri.getRawPath());
    firstRequest.setHeader("delay", "500");
    firstRequest.setHeader("test-request-id", "3");
    HttpGet secondRequest = new HttpGet(secondUri.getRawPath());
    secondRequest.setHeader("test-request-id", "4");

    List<? extends HttpAsyncRequestProducer> requestProducers =
        asList(
            new BasicAsyncRequestProducer(target, firstRequest),
            new BasicAsyncRequestProducer(target, secondRequest));

    IllegalStateException consumerFailure =
        new IllegalStateException("pipelined response consumer failed");
    List<? extends HttpAsyncResponseConsumer<HttpResponse>> responseConsumers =
        asList(
            new BasicAsyncResponseConsumer() {
              @Override
              protected HttpResponse buildResult(HttpContext context) {
                throw consumerFailure;
              }
            },
            new BasicAsyncResponseConsumer());

    CountDownLatch callbackDone = new CountDownLatch(1);
    AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    FutureCallback<List<HttpResponse>> callback =
        new FutureCallback<List<HttpResponse>>() {
          @Override
          public void completed(List<HttpResponse> ignored) {
            callbackDone.countDown();
          }

          @Override
          public void failed(Exception e) {
            try {
              testing.runWithSpan("failure-callback", () -> {});
              callbackFailure.set(e);
            } catch (Throwable t) {
              callbackFailure.set(t);
            } finally {
              callbackDone.countDown();
            }
          }

          @Override
          public void cancelled() {
            callbackDone.countDown();
          }
        };

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        client
                            .execute(target, requestProducers, responseConsumers, null, callback)
                            .get()));

    assertThat(thrown).isInstanceOf(ExecutionException.class).hasCause(consumerFailure);
    assertThat(callbackDone.await(10, SECONDS)).isTrue();
    assertThat(callbackFailure.get()).isSameAs(consumerFailure);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error()),
                span ->
                    assertClientSpan(span, firstUri, "GET", 200, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    assertClientSpan(span, secondUri, "GET", null, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 3)),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 4)),
                span ->
                    span.hasName("failure-callback")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void pipelinedContentFailureEndsCurrentSpanWithError() throws Exception {
    URI uri = resolveAddress("/success");
    HttpHost target = target(uri);
    HttpGet request = new HttpGet(uri.getRawPath());
    request.setHeader("test-request-id", "6");

    IOException consumerFailure = new IOException("pipelined response content failed");
    BasicAsyncResponseConsumer responseConsumer =
        new BasicAsyncResponseConsumer() {
          @Override
          protected void onContentReceived(ContentDecoder decoder, IOControl ioctrl)
              throws IOException {
            throw consumerFailure;
          }
        };

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        client
                            .execute(
                                target,
                                singletonList(new BasicAsyncRequestProducer(target, request)),
                                singletonList(responseConsumer),
                                null,
                                null)
                            .get()));

    assertThat(thrown).isInstanceOf(ExecutionException.class).hasCause(consumerFailure);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error()),
                span ->
                    assertClientSpan(span, uri, "GET", 200, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(consumerFailure),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 6))));
  }

  @Test
  void pipelinedFutureCancellationEndsSpanAndPropagatesCallbackContext() throws Exception {
    URI uri = resolveAddress("/long-request");
    HttpHost target = target(uri);
    HttpGet request = new HttpGet(uri.getRawPath());
    request.setHeader("delay", "500");
    request.setHeader("test-request-id", "7");

    CountDownLatch contentReceived = new CountDownLatch(1);
    BasicAsyncResponseConsumer responseConsumer =
        new BasicAsyncResponseConsumer() {
          @Override
          protected void onContentReceived(ContentDecoder decoder, IOControl ioctrl)
              throws IOException {
            super.onContentReceived(decoder, ioctrl);
            ioctrl.suspendInput();
            contentReceived.countDown();
          }
        };

    CountDownLatch callbackDone = new CountDownLatch(1);
    AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    FutureCallback<List<HttpResponse>> callback =
        new FutureCallback<List<HttpResponse>>() {
          @Override
          public void completed(List<HttpResponse> ignored) {
            callbackFailure.set(new AssertionError("cancelled pipeline completed"));
            callbackDone.countDown();
          }

          @Override
          public void failed(Exception ex) {
            callbackFailure.set(ex);
            callbackDone.countDown();
          }

          @Override
          public void cancelled() {
            try {
              testing.runWithSpan("cancel-callback", () -> {});
            } catch (Throwable t) {
              callbackFailure.set(t);
            } finally {
              callbackDone.countDown();
            }
          }
        };

    Future<List<HttpResponse>> future =
        testing.runWithSpan(
            "parent",
            () ->
                client.execute(
                    target,
                    singletonList(new BasicAsyncRequestProducer(target, request)),
                    singletonList(responseConsumer),
                    null,
                    callback));

    assertThat(contentReceived.await(10, SECONDS)).isTrue();
    future.cancel(true);

    assertThat(callbackDone.await(10, SECONDS)).isTrue();
    assertThat(callbackFailure.get()).isNull();
    assertThat(future.isCancelled()).isTrue();
    assertThat(future.isDone()).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    assertClientSpan(span, uri, "GET", 200, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset()),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 7)),
                span ->
                    span.hasName("cancel-callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void pipelinedConsumerCloseFailureEndsSpanWithError() throws Exception {
    URI uri = resolveAddress("/success");
    HttpHost target = target(uri);
    HttpGet request = new HttpGet(uri.getRawPath());
    request.setHeader("test-request-id", "5");

    List<? extends HttpAsyncRequestProducer> requestProducers =
        singletonList(new BasicAsyncRequestProducer(target, request));

    IllegalStateException closeFailure =
        new IllegalStateException("pipelined response consumer close failed");
    BasicAsyncResponseConsumer delegate = new BasicAsyncResponseConsumer();
    HttpAsyncResponseConsumer<HttpResponse> closeFailingConsumer =
        new HttpAsyncResponseConsumer<HttpResponse>() {
          @Override
          public void responseReceived(HttpResponse response) throws IOException, HttpException {
            delegate.responseReceived(response);
          }

          @Override
          public void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException {
            delegate.consumeContent(decoder, ioctrl);
          }

          @Override
          public void responseCompleted(HttpContext context) {
            delegate.responseCompleted(context);
          }

          @Override
          public void failed(Exception ex) {
            delegate.failed(ex);
          }

          @Override
          public Exception getException() {
            return delegate.getException();
          }

          @Override
          public HttpResponse getResult() {
            return delegate.getResult();
          }

          @Override
          public boolean isDone() {
            return delegate.isDone();
          }

          @Override
          public boolean cancel() {
            return delegate.cancel();
          }

          @Override
          public void close() throws IOException {
            delegate.close();
            throw closeFailure;
          }
        };
    List<? extends HttpAsyncResponseConsumer<HttpResponse>> responseConsumers =
        singletonList(closeFailingConsumer);

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        client
                            .execute(target, requestProducers, responseConsumers, null, null)
                            .get()));

    assertThat(thrown).isInstanceOf(ExecutionException.class).hasCause(closeFailure);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error()),
                span ->
                    assertClientSpan(span, uri, "GET", 200, null)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    assertServerSpan(span)
                        .hasAttributesSatisfyingExactly(equalTo(longKey("test.request.id"), 5))));
  }

  @Test
  void nullGeneratedRequestUsesApacheValidation() throws Exception {
    URI uri = resolveAddress("/success");
    HttpHost target = target(uri);
    HttpAsyncRequestProducer nullRequestProducer =
        new BasicAsyncRequestProducer(target, new HttpGet(uri.getRawPath())) {
          @Override
          public HttpRequest generateRequest() {
            return null;
          }
        };

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        client
                            .execute(
                                target,
                                singletonList(nullRequestProducer),
                                singletonList(new BasicAsyncResponseConsumer()),
                                null,
                                null)
                            .get()));

    assertThat(thrown)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())));
  }

  private static HttpHost target(URI uri) {
    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  private static HttpClientContext context(URI uri) {
    HttpClientContext context = HttpClientContext.create();
    context.setRequestConfig(
        uri.getPath().endsWith("/read-timeout") ? READ_TIMEOUT_REQUEST_CONFIG : REQUEST_CONFIG);
    return context;
  }

  private static int getResponseCode(HttpResponse response) {
    try {
      if (response.getEntity() != null && response.getEntity().getContent() != null) {
        response.getEntity().getContent().close();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return response.getStatusLine().getStatusCode();
  }

  private static String fullPathFromUri(URI uri) {
    StringBuilder builder = new StringBuilder();
    if (uri.getPath() != null) {
      builder.append(uri.getPath());
    }

    if (uri.getQuery() != null) {
      builder.append('?').append(uri.getQuery());
    }

    if (uri.getFragment() != null) {
      builder.append('#').append(uri.getFragment());
    }
    return builder.toString();
  }

  private static class ResponseCallback implements FutureCallback<List<HttpResponse>> {
    private final HttpClientResult result;

    private ResponseCallback(HttpClientResult result) {
      this.result = result;
    }

    @Override
    public void completed(List<HttpResponse> responses) {
      result.complete(getResponseCode(responses.get(0)));
    }

    @Override
    public void failed(Exception e) {
      result.complete(e);
    }

    @Override
    public void cancelled() {
      result.complete(new CancellationException());
    }
  }
}
