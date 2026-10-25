/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.AsyncContentProvider;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpContent;
import org.eclipse.jetty.client.HttpSender;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JettyCallerContextTest {

  private static final ContextKey<String> VALUE = ContextKey.named("jetty-test-value");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ConcurrentHashMap<String, String> propagation = new ConcurrentHashMap<>();
  private HttpClient client;
  private String url;

  @BeforeEach
  void start() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress("localhost", PortUtils.findOpenPort()), 0);
    cleanup.deferCleanup(() -> server.stop(0));
    server.createContext(
        "/",
        exchange -> {
          if (exchange.getRequestURI().getPath().equals("/early")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
          }
          if (exchange.getRequestURI().getPath().equals("/reject")) {
            exchange.close();
            return;
          }
          String header = exchange.getRequestHeaders().getFirst("traceparent");
          if (header != null) {
            propagation.put(exchange.getRequestURI().getPath(), header);
          }
          String path = exchange.getRequestURI().getPath();
          if (path.equals("/redirect") || path.equals("/redirect-again")) {
            exchange
                .getResponseHeaders()
                .add("Location", path.equals("/redirect") ? "/redirect-again" : "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
          }
          if (path.equals("/auth")
              && exchange.getRequestHeaders().getFirst("Authorization") == null) {
            propagation.put("/challenge", header);
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"test\"");
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          byte[] buffer = new byte[128];
          while (exchange.getRequestBody().read(buffer) != -1) {}
          byte[] response = "response".getBytes(UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    url = "http://localhost:" + server.getAddress().getPort();
    client = new HttpClient();
    client.start();
    cleanup.deferCleanup(client::stop);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listenersAndAsyncBodyUseFullCallerContext(boolean withSpan) throws Exception {
    Span parent =
        withSpan
            ? testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan()
            : Span.getInvalid();
    Context caller =
        (withSpan ? Context.root().with(parent) : Context.root()).with(VALUE, "caller");
    Observations observations = new Observations();
    Body body = new Body(observations);
    CompletableFuture<Result> completed = new CompletableFuture<>();
    Request request = client.newRequest(url + "/body").method("POST").content(body);
    request
        .onRequestQueued(r -> observations.record("queued"))
        .onRequestBegin(r -> observations.record("begin"))
        .onRequestHeaders(r -> observations.record("headers"))
        .onRequestCommit(r -> observations.record("commit"))
        .onRequestContent((r, bytes) -> observations.record("content"))
        .onRequestSuccess(r -> observations.record("success"))
        .onResponseContent((r, bytes) -> observations.record("response"));
    List<Request.RequestListener> listeners = new ArrayList<>(request.getRequestListeners(null));
    try (Scope ignored = caller.makeCurrent()) {
      request.send(
          result -> {
            observations.record("complete");
            completed.complete(result);
          });
      assertContext(Context.current(), caller);
      assertThat(request.getContent()).isSameAs(body);
      for (int i = 0; i < listeners.size(); i++) {
        assertThat(request.getRequestListeners(null).get(i)).isSameAs(listeners.get(i));
      }
    }

    assertThat(body.ready.await(10, SECONDS)).isTrue();
    assertThat(body.originalIterator).isSameAs(body.iterator);
    Context unrelated = body.creationContext.with(VALUE, "unrelated");
    Thread producer =
        new Thread(
            () -> {
              try (Scope ignored = unrelated.makeCurrent()) {
                body.available = true;
                body.listener.onContent();
                observations.restored = Context.current();
              }
            });
    producer.start();
    producer.join(10000);
    assertThat(producer.isAlive()).isFalse();
    assertThat(completed.get(10, SECONDS).isSucceeded()).isTrue();
    assertThat(body.closed.await(10, SECONDS)).isTrue();
    parent.end();

    observations.assertCaller(caller);
    assertContext(observations.restored, unrelated);
    assertThat(observations.events)
        .contains(
            "queued",
            "begin",
            "headers",
            "commit",
            "content",
            "success",
            "response",
            "complete",
            "iterator",
            "hasNext",
            "next",
            "succeeded",
            "close");
    testing.waitAndAssertTraces(
        trace -> {
          if (withSpan) {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            data ->
                                assertThat(propagation.get("/body")).contains(data.getSpanId())));
          } else {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .satisfies(
                            data ->
                                assertThat(propagation.get("/body")).contains(data.getSpanId())));
          }
        });
  }

  @Test
  void ordinaryBodyUsesCallerContext() throws Exception {
    Observations observations = new Observations();
    Body body = new Body(observations);
    body.available = true;
    ContentProvider provider =
        new ContentProvider() {
          @Override
          public long getLength() {
            return body.getLength();
          }

          @Override
          public Iterator<ByteBuffer> iterator() {
            return body.iterator();
          }
        };
    Context caller = Context.root().with(VALUE, "ordinary");
    Request request = client.newRequest(url + "/ordinary").method("POST").content(provider);
    try (Scope ignored = caller.makeCurrent()) {
      assertThat(request.send().getStatus()).isEqualTo(200);
      assertThat(request.getContent()).isSameAs(provider);
    }
    assertThat(body.closed.await(10, SECONDS)).isTrue();
    observations.assertCaller(caller);
    assertThat(observations.events).contains("iterator", "next", "succeeded", "close");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void requestCallbackCanStartIndependentClientWork() throws Exception {
    CompletableFuture<Result> nested = new CompletableFuture<>();
    CompletableFuture<Result> completed = new CompletableFuture<>();
    testing.runWithSpan(
        "parent",
        () ->
            client
                .newRequest(url + "/outer")
                .onRequestBegin(
                    request -> client.newRequest(url + "/nested").send(nested::complete))
                .send(completed::complete));
    assertThat(completed.get(10, SECONDS).isSucceeded()).isTrue();
    assertThat(nested.get(10, SECONDS).isSucceeded()).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void reentrantAbortPreservesExplicitNestedContext() throws Exception {
    CompletableFuture<Result> completed = new CompletableFuture<>();
    CompletableFuture<Context> failureContext = new CompletableFuture<>();
    Context caller = Context.root().with(VALUE, "caller");
    Context nested = caller.with(VALUE, "nested");
    Request request = client.newRequest(url + "/nested-abort");
    request.onRequestFailure((r, failure) -> failureContext.complete(Context.current()));
    request.onRequestBegin(
        r -> {
          try (Scope ignored = nested.makeCurrent()) {
            r.abort(new IllegalStateException("nested abort"));
            assertContext(Context.current(), nested);
          }
          assertThat(Context.current().get(VALUE)).isEqualTo("caller");
        });
    try (Scope ignored = caller.makeCurrent()) {
      request.send(completed::complete);
    }
    assertThat(completed.get(10, SECONDS).isFailed()).isTrue();
    assertThat(failureContext.get(10, SECONDS).get(VALUE)).isEqualTo("nested");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void crossRequestReentryPreservesExplicitNestedContext() throws Exception {
    Span parent = testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Context caller = Context.root().with(parent).with(VALUE, "caller");
    Context nested = caller.with(VALUE, "nested");
    CompletableFuture<Result> completedA = new CompletableFuture<>();
    CompletableFuture<Result> completedB = new CompletableFuture<>();
    CompletableFuture<Context> queuedContext = new CompletableFuture<>();
    CompletableFuture<Context> failureContext = new CompletableFuture<>();
    CompletableFuture<Context> restoredAfterAbort = new CompletableFuture<>();
    CompletableFuture<Context> restoredAfterNestedScope = new CompletableFuture<>();
    CompletableFuture<Context> restoredAfterRequestB = new CompletableFuture<>();
    CompletableFuture<Context> restoredAfterRequestA = new CompletableFuture<>();
    AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    Request requestA =
        client
            .newRequest(url + "/request-a")
            .onRequestFailure((request, failure) -> failureContext.complete(Context.current()));
    requestA.onRequestBegin(
        request -> {
          try {
            client
                .newRequest(url + "/request-b")
                .onRequestQueued(
                    requestB -> {
                      queuedContext.complete(Context.current());
                      try (Scope ignored = nested.makeCurrent()) {
                        requestA.abort(new IllegalStateException("abort request A from B"));
                        restoredAfterAbort.complete(Context.current());
                      }
                      restoredAfterNestedScope.complete(Context.current());
                    })
                .send(completedB::complete);
            restoredAfterRequestB.complete(Context.current());
          } catch (RuntimeException | Error e) {
            callbackFailure.set(e);
          } finally {
            restoredAfterRequestA.complete(Context.current());
          }
        });
    assertThat(requestA.getRequestListeners(Request.BeginListener.class)).hasSize(1);
    try {
      try (Scope ignored = caller.makeCurrent()) {
        requestA.send(completedA::complete);
        assertContext(Context.current(), caller);
      }
      Result resultA = completedA.get(10, SECONDS);
      Result resultB = completedB.get(10, SECONDS);
      assertThat(callbackFailure.get()).isNull();
      assertThat(resultA.isFailed()).isTrue();
      assertThat(resultB.isSucceeded()).isTrue();
      assertContext(queuedContext.get(10, SECONDS), caller);
      assertContext(failureContext.get(10, SECONDS), nested);
      assertContext(restoredAfterAbort.get(10, SECONDS), nested);
      assertContext(restoredAfterNestedScope.get(10, SECONDS), caller);
      assertContext(restoredAfterRequestB.get(10, SECONDS), caller);
      assertContext(restoredAfterRequestA.get(10, SECONDS), caller);
    } finally {
      parent.end();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void throwingContentIteratorRestoresProducerContextAndCallbackFrame() throws Exception {
    Span parent = testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Context caller = Context.root().with(parent).with(VALUE, "caller");
    Span producerSpan =
        testing
            .getOpenTelemetry()
            .getTracer("test")
            .spanBuilder("producer")
            .setParent(caller)
            .startSpan();
    Context producer = Context.root().with(producerSpan).with(VALUE, "producer");
    IllegalStateException failure = new IllegalStateException("iterator failure");
    ThrowingBody body = new ThrowingBody(failure);
    CompletableFuture<Result> completed = new CompletableFuture<>();
    CompletableFuture<Context> requestFailureContext = new CompletableFuture<>();
    Request request =
        client
            .newRequest(url + "/throwing")
            .method("POST")
            .content(body)
            .onRequestFailure(
                (ignored, requestFailure) -> requestFailureContext.complete(Context.current()));
    try {
      try (Scope ignored = caller.makeCurrent()) {
        request.send(completed::complete);
      }
      assertThat(body.ready.await(10, SECONDS)).isTrue();
      Thread producerThread =
          new Thread(
              () -> {
                try (Scope ignored = producer.makeCurrent()) {
                  body.available = true;
                  body.listener.onContent();
                  body.restoredContext.complete(Context.current());
                }
              });
      producerThread.start();
      producerThread.join(10000);
      assertThat(producerThread.isAlive()).isFalse();

      Result result = completed.get(10, SECONDS);
      assertThat(result.isFailed()).isTrue();
      assertThat(body.iteratorThread.get()).isSameAs(producerThread);
      assertContext(body.iteratorContext.get(10, SECONDS), caller);
      assertThat(body.iteratorFailure.get(10, SECONDS)).isSameAs(failure);
      assertContext(body.iteratorFailureContext.get(10, SECONDS), caller);
      assertContext(requestFailureContext.get(10, SECONDS), caller);
      assertContext(body.restoredContext.get(10, SECONDS), producer);
    } finally {
      producerSpan.end();
      parent.end();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("producer").hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void responseCallbackAbortPreservesExplicitNestedContext() throws Exception {
    CompletableFuture<Result> completed = new CompletableFuture<>();
    CompletableFuture<Context> failureContext = new CompletableFuture<>();
    CompletableFuture<Context> restored = new CompletableFuture<>();
    Context caller = Context.root().with(VALUE, "caller");
    Context nested = Context.root().with(VALUE, "nested-response");
    Body body = new Body(new Observations());
    Request request = client.newRequest(url + "/early").method("POST").content(body);
    request.onRequestFailure((r, failure) -> failureContext.complete(Context.current()));
    request.onResponseHeaders(
        response -> {
          try (Scope ignored = nested.makeCurrent()) {
            request.abort(new IllegalStateException("abort from response"));
            restored.complete(Context.current());
          }
        });
    try (Scope ignored = caller.makeCurrent()) {
      request.send(completed::complete);
    }
    assertThat(completed.get(10, SECONDS).isFailed()).isTrue();
    assertContext(failureContext.get(10, SECONDS), nested);
    assertContext(restored.get(10, SECONDS), nested);
    assertThat(body.closed.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void deferredChunkSuccessUsesCallerAndRestoresProducerContext() throws Exception {
    Observations observations = new Observations();
    DeferredContentProvider body = new DeferredContentProvider();
    Context caller = Context.root().with(VALUE, "caller");
    CompletableFuture<Result> completed = new CompletableFuture<>();
    CountDownLatch committed = new CountDownLatch(1);
    Request request =
        client
            .newRequest(url + "/deferred")
            .method("POST")
            .content(body)
            .onRequestCommit(r -> committed.countDown());
    try (Scope ignored = caller.makeCurrent()) {
      request.send(completed::complete);
    }
    assertThat(committed.await(10, SECONDS)).isTrue();
    CompletableFuture<Void> chunk = new CompletableFuture<>();
    Context unrelated = Context.root().with(VALUE, "producer");
    try (Scope ignored = unrelated.makeCurrent()) {
      body.offer(
          ByteBuffer.wrap("body".getBytes(UTF_8)),
          new Callback() {
            @Override
            public void succeeded() {
              observations.record("chunk");
              chunk.complete(null);
            }

            @Override
            public void failed(Throwable failure) {
              chunk.completeExceptionally(failure);
            }
          });
      body.close();
      assertContext(Context.current(), unrelated);
    }
    chunk.get(10, SECONDS);
    assertThat(completed.get(10, SECONDS).isSucceeded()).isTrue();
    observations.assertCaller(caller);
    assertThat(observations.events).contains("chunk");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void abortRestoresContextAndClosesBody() throws Exception {
    Observations observations = new Observations();
    Body body = new Body(observations);
    Context caller = Context.root().with(VALUE, "caller");
    CompletableFuture<Result> completed = new CompletableFuture<>();
    Request request = client.newRequest(url + "/abort").method("POST").content(body);
    request.onRequestFailure((r, failure) -> observations.record("failure"));
    try (Scope ignored = caller.makeCurrent()) {
      request.send(completed::complete);
    }
    assertThat(body.ready.await(10, SECONDS)).isTrue();
    Context unrelated = Context.root().with(VALUE, "aborter");
    try (Scope ignored = unrelated.makeCurrent()) {
      request.abort(new IllegalStateException("abort upload"));
      assertContext(Context.current(), unrelated);
    }
    assertThat(completed.get(10, SECONDS).isFailed()).isTrue();
    assertThat(body.closed.await(10, SECONDS)).isTrue();
    observations.assertCaller(caller);
    assertThat(observations.events).contains("failure", "close");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void deferredChunkFailureUsesCallerContext() throws Exception {
    Observations observations = new Observations();
    DeferredContentProvider body = new DeferredContentProvider();
    CompletableFuture<Throwable> chunk = new CompletableFuture<>();
    body.offer(
        ByteBuffer.allocate(16 * 1024 * 1024),
        new Callback() {
          @Override
          public void succeeded() {
            chunk.completeExceptionally(new AssertionError("Upload should fail"));
          }

          @Override
          public void failed(Throwable failure) {
            observations.record("chunk-failure");
            chunk.complete(failure);
          }
        });
    body.close();
    Context caller = Context.root().with(VALUE, "caller");
    CompletableFuture<Result> completed = new CompletableFuture<>();
    try (Scope ignored = caller.makeCurrent()) {
      client.newRequest(url + "/reject").method("POST").content(body).send(completed::complete);
    }
    assertThat(completed.get(10, SECONDS).isFailed()).isTrue();
    assertThat(chunk.get(10, SECONDS)).isNotNull();
    observations.assertCaller(caller);
    assertThat(observations.events).contains("chunk-failure");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void reusedRequestCapturesEachCaller() throws Exception {
    ConcurrentLinkedQueue<String> callers = new ConcurrentLinkedQueue<>();
    Request request =
        client
            .newRequest(url + "/reuse")
            .onRequestBegin(r -> callers.add(Context.current().get(VALUE)));
    int requestListenerCount = 0;
    int responseListenerCount = 0;
    for (int i = 0; i < 5; i++) {
      try (Scope ignored = Context.root().with(VALUE, "caller-" + i).makeCurrent()) {
        assertThat(request.send().getStatus()).isEqualTo(200);
      }
      if (i == 0) {
        requestListenerCount = request.getRequestListeners(null).size();
        responseListenerCount = responseListenerCount(request);
      } else {
        assertThat(request.getRequestListeners(null)).hasSize(requestListenerCount);
        assertThat(responseListenerCount(request)).isEqualTo(responseListenerCount + i);
      }
    }
    assertThat(callers).containsExactly("caller-0", "caller-1", "caller-2", "caller-3", "caller-4");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"redirect", "auth"})
  void protocolFollowupsKeepTheOriginalOperation(String protocol) throws Exception {
    client
        .getAuthenticationStore()
        .addAuthentication(new BasicAuthentication(URI.create(url), "test", "user", "password"));
    Observations observations = new Observations();
    client
        .getRequestListeners()
        .add(
            new Request.Listener.Adapter() {
              @Override
              public void onBegin(Request request) {
                observations.record("begin");
              }
            });
    Span parent = testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Context caller = Context.root().with(parent).with(VALUE, "protocol");
    ContentResponse response;
    try (Scope ignored = caller.makeCurrent()) {
      response = client.newRequest(url + "/" + protocol).send();
      assertThat(response.getStatus()).isEqualTo(200);
    } finally {
      parent.end();
    }
    observations.assertCaller(caller);
    assertThat(observations.events).hasSize(protocol.equals("redirect") ? 3 : 2);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            data ->
                                assertThat(propagation.values())
                                    .allSatisfy(
                                        value -> assertThat(value).contains(data.getSpanId())))));
    try (Scope ignored = Context.root().with(VALUE, "reused-continuation").makeCurrent()) {
      assertThat(response.getRequest().send().getStatus()).isEqualTo(200);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void reusedRedirectDoesNotTraceProtocolFollowups() throws Exception {
    AtomicBoolean reused = new AtomicBoolean();
    CompletableFuture<Result> terminal = new CompletableFuture<>();
    Request original =
        client
            .newRequest(url + "/redirect")
            .onComplete(
                result -> {
                  if (reused.get() && result.getResponse().getStatus() == 200) {
                    terminal.complete(result);
                  }
                });
    Request request = original.send().getRequest();
    Observations observations = new Observations();
    client
        .getRequestListeners()
        .add(
            new Request.Listener.Adapter() {
              @Override
              public void onBegin(Request r) {
                observations.record("begin");
              }
            });
    Context caller = Context.root().with(VALUE, "reused-redirect");
    reused.set(true);
    try (Scope ignored = caller.makeCurrent()) {
      // Jetty completes a reused request on the intermediate response, before its protocol handler.
      assertThat(request.path("/redirect").send().getStatus()).isEqualTo(302);
    }
    assertThat(terminal.get(10, SECONDS).isSucceeded()).isTrue();
    observations.assertCaller(caller);
    assertThat(observations.events).hasSize(3);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  private static void assertContext(Context actual, Context expected) {
    assertThat(actual.get(VALUE)).isEqualTo(expected.get(VALUE));
    assertThat(Span.fromContext(actual).getSpanContext())
        .isEqualTo(Span.fromContext(expected).getSpanContext());
  }

  private static int responseListenerCount(Request request) throws ReflectiveOperationException {
    Field field = request.getClass().getDeclaredField("responseListeners");
    field.setAccessible(true);
    return ((List<?>) field.get(request)).size();
  }

  private static class Observations {
    final ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Context> contexts = new ConcurrentLinkedQueue<>();
    volatile Context restored;

    void record(String event) {
      events.add(event);
      contexts.add(Context.current());
    }

    void assertCaller(Context caller) {
      assertThat(contexts).isNotEmpty();
      for (Context context : contexts) {
        assertContext(context, caller);
      }
    }
  }

  private static class Body implements AsyncContentProvider, Callback {
    final CountDownLatch ready = new CountDownLatch(1);
    final CountDownLatch closed = new CountDownLatch(1);
    final Observations observations;
    final BodyIterator iterator = new BodyIterator();
    volatile Listener listener;
    volatile Context creationContext;
    volatile Object originalIterator;
    volatile boolean available;
    boolean consumed;

    Body(Observations observations) {
      this.observations = observations;
    }

    @Override
    public void setListener(Listener listener) {
      observations.record("setListener");
      this.listener = listener;
      try {
        Field content = HttpSender.class.getDeclaredField("content");
        content.setAccessible(true);
        Field iterator = HttpContent.class.getDeclaredField("iterator");
        iterator.setAccessible(true);
        originalIterator = iterator.get(content.get(listener));
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
      ready.countDown();
    }

    @Override
    public long getLength() {
      observations.record("length");
      return -1;
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
      observations.record("iterator");
      creationContext = Context.current();
      return iterator;
    }

    @Override
    public void succeeded() {}

    @Override
    public void failed(Throwable failure) {
      observations.record("failed");
    }

    private class BodyIterator implements Iterator<ByteBuffer>, Callback, Closeable {
      @Override
      public boolean hasNext() {
        observations.record("hasNext");
        return !consumed;
      }

      @Override
      public ByteBuffer next() {
        observations.record("next");
        if (consumed) {
          throw new NoSuchElementException();
        }
        if (!available) {
          return null;
        }
        consumed = true;
        return ByteBuffer.wrap("body".getBytes(UTF_8));
      }

      @Override
      public void succeeded() {
        observations.record("succeeded");
      }

      @Override
      public void failed(Throwable failure) {
        observations.record("failed");
      }

      @Override
      public void close() {
        observations.record("close");
        closed.countDown();
      }
    }
  }

  private static class ThrowingBody implements AsyncContentProvider {
    final CountDownLatch ready = new CountDownLatch(1);
    final CompletableFuture<Context> iteratorContext = new CompletableFuture<>();
    final CompletableFuture<Throwable> iteratorFailure = new CompletableFuture<>();
    final CompletableFuture<Context> iteratorFailureContext = new CompletableFuture<>();
    final CompletableFuture<Context> restoredContext = new CompletableFuture<>();
    final AtomicReference<Thread> iteratorThread = new AtomicReference<>();
    final RuntimeException failure;
    final ThrowingIterator iterator = new ThrowingIterator();
    volatile boolean available;
    volatile Listener listener;

    ThrowingBody(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void setListener(Listener listener) {
      this.listener = listener;
      ready.countDown();
    }

    @Override
    public long getLength() {
      return -1;
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
      return iterator;
    }

    private class ThrowingIterator implements Iterator<ByteBuffer>, Callback {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public ByteBuffer next() {
        if (!available) {
          return null;
        }
        iteratorThread.set(Thread.currentThread());
        iteratorContext.complete(Context.current());
        throw failure;
      }

      @Override
      public void succeeded() {}

      @Override
      public void failed(Throwable failure) {
        iteratorFailure.complete(failure);
        iteratorFailureContext.complete(Context.current());
      }
    }
  }
}
