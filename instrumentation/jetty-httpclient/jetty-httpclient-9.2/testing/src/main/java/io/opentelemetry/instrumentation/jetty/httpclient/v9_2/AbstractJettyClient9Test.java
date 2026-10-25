/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractJettyClient9Test extends AbstractHttpClientTest<Request> {

  private static final ContextKey<String> CALLER_KEY = ContextKey.named("caller");

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private HttpClient client;
  private HttpClient httpsClient;

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory sslContextFactory);

  @BeforeEach
  void before() throws Exception {
    // Start the main Jetty HttpClient and a https client
    client = createStandardClient();
    client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis());
    client.start();
    cleanup.deferCleanup(client::stop);

    SslContextFactory tlsCtx = new SslContextFactory();
    httpsClient = createHttpsClient(tlsCtx);
    httpsClient.setFollowRedirects(false);
    httpsClient.start();
    cleanup.deferCleanup(httpsClient::stop);
  }

  @Test
  void requestCallbacksAndBodyGenerationUseCallerContext()
      throws ExecutionException, InterruptedException, TimeoutException {
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    AtomicReference<Context> requestListenerContext = new AtomicReference<>();
    AtomicReference<Context> responseConsumptionContext = new AtomicReference<>();
    AtomicReference<Context> bodyGenerationContext = new AtomicReference<>();
    AtomicReference<Thread> bodyGenerationThread = new AtomicReference<>();
    observePreparation(preparationContext);

    Span callerSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("caller").startSpan();
    Context callerContext = Context.current().with(callerSpan).with(CALLER_KEY, "caller");
    InputStreamContentProvider contentProvider =
        new InputStreamContentProvider(new ByteArrayInputStream("request body".getBytes(UTF_8))) {
          @Override
          protected ByteBuffer onRead(byte[] buffer, int offset, int length) {
            bodyGenerationContext.set(Context.current());
            bodyGenerationThread.set(Thread.currentThread());
            return super.onRead(buffer, offset, length);
          }
        };
    Request request =
        client
            .newRequest(resolveAddress("/success"))
            .method("POST")
            .content(contentProvider)
            .onRequestBegin(
                ignored -> {
                  requestListenerContext.set(Context.current());
                  testing.runWithSpan("request-callback", () -> {});
                })
            .onResponseContent(
                (response, content) -> responseConsumptionContext.set(Context.current()));

    try {
      try (Scope ignored = callerContext.makeCurrent()) {
        assertThat(request.send().getStatus()).isEqualTo(200);
        assertCallerContext(Context.current(), callerContext);
      }
      assertCallerContext(preparationContext.get(), callerContext);
      assertCallerContext(requestListenerContext.get(), callerContext);
      assertCallerContext(responseConsumptionContext.get(), callerContext);
      assertCallerContext(bodyGenerationContext.get(), callerContext);
      assertThat(bodyGenerationThread.get()).isNotSameAs(Thread.currentThread());
    } finally {
      callerSpan.end();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("caller").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("request-callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))));
  }

  @Test
  void asynchronousRequestCallbacksAndBodyGenerationUseCallerContextWithoutParent()
      throws InterruptedException {
    Context callerContext = Context.current().with(CALLER_KEY, "caller");
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    AtomicReference<Context> requestListenerContext = new AtomicReference<>();
    AtomicReference<Context> responseConsumptionContext = new AtomicReference<>();
    AtomicReference<Context> bodyGenerationContext = new AtomicReference<>();
    AtomicReference<Thread> bodyGenerationThread = new AtomicReference<>();
    observePreparation(preparationContext);

    InputStreamContentProvider contentProvider =
        new InputStreamContentProvider(new ByteArrayInputStream("request body".getBytes(UTF_8))) {
          @Override
          protected ByteBuffer onRead(byte[] buffer, int offset, int length) {
            bodyGenerationContext.set(Context.current());
            bodyGenerationThread.set(Thread.currentThread());
            return super.onRead(buffer, offset, length);
          }
        };
    Request request =
        client
            .newRequest(resolveAddress("/success"))
            .method("POST")
            .content(contentProvider)
            .onRequestBegin(ignored -> requestListenerContext.set(Context.current()))
            .onResponseContent(
                (response, content) -> responseConsumptionContext.set(Context.current()));
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<Integer> responseStatus = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread sendingThread = Thread.currentThread();

    try (Scope ignored = callerContext.makeCurrent()) {
      request.send(
          result -> {
            failure.set(result.getFailure());
            if (result.getResponse() != null) {
              responseStatus.set(result.getResponse().getStatus());
            }
            completed.countDown();
          });
      assertCallerContext(Context.current(), callerContext);
      assertThat(completed.await(10, SECONDS)).isTrue();
    }

    assertThat(failure.get()).isNull();
    assertThat(responseStatus.get()).isEqualTo(200);
    assertCallerContext(preparationContext.get(), callerContext);
    assertCallerContext(requestListenerContext.get(), callerContext);
    assertCallerContext(responseConsumptionContext.get(), callerContext);
    assertCallerContext(bodyGenerationContext.get(), callerContext);
    assertThat(bodyGenerationThread.get()).isNotSameAs(sendingThread);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("POST"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
  }

  private void observePreparation(AtomicReference<Context> preparationContext) {
    client
        .getProxyConfiguration()
        .getProxies()
        .add(
            new HttpProxy("localhost", 1) {
              @Override
              public boolean matches(Origin origin) {
                preparationContext.set(Context.current());
                return false;
              }
            });
  }

  private static void assertCallerContext(Context actual, Context callerContext) {
    assertThat(actual.get(CALLER_KEY)).isEqualTo("caller");
    assertThat(Span.fromContext(actual).getSpanContext())
        .isEqualTo(Span.fromContext(callerContext).getSpanContext());
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.spanEndsAfterBody();
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClient theClient = "https".equalsIgnoreCase(uri.getScheme()) ? httpsClient : client;
    Request request = theClient.newRequest(uri).method(method).agent("Jetty");
    headers.forEach(request::header);

    if (uri.getPath().endsWith("/read-timeout")) {
      request.timeout(READ_TIMEOUT.toMillis(), MILLISECONDS);
    } else if (uri.toString().contains("192.0.2.1")) {
      request.timeout(CONNECTION_TIMEOUT.toMillis(), MILLISECONDS);
    }

    return request;
  }

  @Override
  public int sendRequest(Request httpRequest, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    return httpRequest.send().getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    JettyClientListener jcl = new JettyClientListener();
    request.onRequestFailure(jcl);
    request.onResponseFailure(jcl);
    request.send(
        result -> {
          if (jcl.failure != null) {
            httpClientResult.complete(jcl.failure);
            return;
          }
          httpClientResult.complete(result.getResponse().getStatus());
        });
  }

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    private volatile Throwable failure;

    @Override
    public void onFailure(Request requestF, Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onFailure(Response responseF, Throwable failure) {
      this.failure = failure;
    }
  }
}
