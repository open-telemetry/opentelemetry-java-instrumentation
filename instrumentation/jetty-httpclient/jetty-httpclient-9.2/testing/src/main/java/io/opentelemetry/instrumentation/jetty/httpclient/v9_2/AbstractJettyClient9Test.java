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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractJettyClient9Test extends AbstractHttpClientTest<Request> {

  private static final ContextKey<String> CALLER_KEY = ContextKey.named("caller");
  private static final ContextKey<String> OTHER_KEY = ContextKey.named("other");

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

  @Test
  void contentProviderWithSealedApplicationInterfaceIsSupported(@TempDir Path tempDir)
      throws IOException,
          InterruptedException,
          ExecutionException,
          TimeoutException,
          ReflectiveOperationException {
    String version = System.getProperty("java.specification.version");
    int javaVersion = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
    Assumptions.assumeTrue(javaVersion >= 17);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assumptions.assumeTrue(compiler != null);
    Path packageDir = Files.createDirectories(tempDir.resolve("sealedprovider"));
    Path marker = packageDir.resolve("Marker.java");
    Path provider = packageDir.resolve("SealedProvider.java");
    Files.write(
        marker,
        ("package sealedprovider;\n" + "public sealed interface Marker permits SealedProvider {}\n")
            .getBytes(UTF_8));
    Files.write(
        provider,
        ("package sealedprovider;\n"
                + "import java.nio.ByteBuffer;\n"
                + "import java.util.Iterator;\n"
                + "import java.util.NoSuchElementException;\n"
                + "import org.eclipse.jetty.client.api.ContentProvider;\n"
                + "public final class SealedProvider implements ContentProvider.Typed, Marker {\n"
                + "  public String getContentType() { return \"text/plain\"; }\n"
                + "  public long getLength() { return 7; }\n"
                + "  public Iterator<ByteBuffer> iterator() {\n"
                + "    return new Iterator<ByteBuffer>() {\n"
                + "      private boolean hasNext = true;\n"
                + "      public boolean hasNext() { return hasNext; }\n"
                + "      public ByteBuffer next() {\n"
                + "        if (!hasNext) throw new NoSuchElementException();\n"
                + "        hasNext = false;\n"
                + "        return ByteBuffer.wrap(\"payload\".getBytes());\n"
                + "      }\n"
                + "    };\n"
                + "  }\n"
                + "}\n")
            .getBytes(UTF_8));
    int compileResult =
        compiler.run(
            null,
            null,
            null,
            "--release",
            "17",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            tempDir.toString(),
            marker.toString(),
            provider.toString());
    assertThat(compileResult).isZero();

    try (URLClassLoader classLoader =
        new URLClassLoader(
            new URL[] {tempDir.toUri().toURL()}, ContentProvider.class.getClassLoader())) {
      ContentProvider contentProvider =
          (ContentProvider)
              classLoader
                  .loadClass("sealedprovider.SealedProvider")
                  .getDeclaredConstructor()
                  .newInstance();
      assertThat(
              client
                  .newRequest(resolveAddress("/success"))
                  .method("POST")
                  .content(contentProvider)
                  .send()
                  .getStatus())
          .isEqualTo(200);
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("POST"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent(),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
  }

  @Test
  void deferredContentProviderCallbacksUseCallerContext() throws IOException, InterruptedException {
    AtomicReference<String> receivedPayload = new AtomicReference<>();
    CountDownLatch requestCompleted = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          receivedPayload.set(new String(readRequestBody(exchange), UTF_8));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
          requestCompleted.countDown();
        });
    server.start();

    DeferredContentProvider contentProvider = new DeferredContentProvider();
    Context enteringContext = Context.current();
    Context callerContext = enteringContext.with(CALLER_KEY, "caller");
    Context producerContext = enteringContext.with(OTHER_KEY, "producer");
    CountDownLatch chunkCompleted = new CountDownLatch(1);
    CountDownLatch sendCompleted = new CountDownLatch(1);
    AtomicReference<Context> chunkContext = new AtomicReference<>();
    AtomicReference<Context> chunkContextAfterNestedScope = new AtomicReference<>();
    AtomicReference<Throwable> chunkFailure = new AtomicReference<>();
    AtomicReference<Throwable> sendFailure = new AtomicReference<>();
    AtomicReference<Context> sendContext = new AtomicReference<>();
    AtomicInteger chunkCallbackCount = new AtomicInteger();
    AtomicInteger sendCallbackCount = new AtomicInteger();
    Callback callback =
        new Callback() {
          @Override
          public void succeeded() {
            chunkContext.set(Context.current());
            try (Scope ignored = producerContext.makeCurrent()) {
              assertThat(Context.current().get(OTHER_KEY)).isEqualTo("producer");
            }
            chunkContextAfterNestedScope.set(Context.current());
            chunkCallbackCount.incrementAndGet();
            chunkCompleted.countDown();
          }

          @Override
          public void failed(Throwable failure) {
            chunkFailure.set(failure);
            chunkContext.set(Context.current());
            chunkCallbackCount.incrementAndGet();
            chunkCompleted.countDown();
          }
        };

    try {
      try (Scope ignored = callerContext.makeCurrent()) {
        client
            .newRequest("http://localhost:" + server.getAddress().getPort() + "/")
            .method("POST")
            .content(contentProvider)
            .send(
                result -> {
                  sendFailure.set(result.getFailure());
                  sendContext.set(Context.current());
                  sendCallbackCount.incrementAndGet();
                  sendCompleted.countDown();
                });
        assertCallerContext(Context.current(), callerContext);
      }

      try (Scope ignored = producerContext.makeCurrent()) {
        assertThat(
                contentProvider.offer(
                    ByteBuffer.wrap("deferred payload".getBytes(UTF_8)), callback))
            .isTrue();
        contentProvider.close();
        assertThat(Context.current().get(OTHER_KEY)).isEqualTo("producer");
      }
      assertThat(Context.current()).isSameAs(enteringContext);

      assertThat(chunkCompleted.await(10, SECONDS)).isTrue();
      assertThat(sendCompleted.await(10, SECONDS)).isTrue();
      assertThat(requestCompleted.await(10, SECONDS)).isTrue();
      assertThat(receivedPayload.get()).isEqualTo("deferred payload");
      assertThat(chunkFailure.get()).isNull();
      assertThat(sendFailure.get()).isNull();
      assertThat(chunkCallbackCount.get()).isGreaterThan(0);
      assertThat(sendCallbackCount.get()).isEqualTo(1);
      assertCallerContext(chunkContext.get(), callerContext);
      assertCallerContext(chunkContextAfterNestedScope.get(), callerContext);
      assertCallerContext(sendContext.get(), callerContext);
      assertThat(Span.fromContext(callerContext).getSpanContext().isValid()).isFalse();
    } finally {
      server.stop(0);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void deferredContentProviderFailureCallbackUsesCallerContext()
      throws IOException, InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch contentStarted = new CountDownLatch(1);
    CountDownLatch releaseServer = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          requestStarted.countDown();
          try (InputStream input = exchange.getRequestBody()) {
            if (input.read() != -1) {
              contentStarted.countDown();
            }
            try {
              releaseServer.await(10, SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          } catch (IOException ignored) {
            return;
          } finally {
            exchange.close();
          }
        });
    server.start();

    DeferredContentProvider contentProvider = new DeferredContentProvider();
    Context callerContext = Context.current().with(CALLER_KEY, "caller");
    Context producerContext = Context.current().with(OTHER_KEY, "producer");
    CountDownLatch chunkCompleted = new CountDownLatch(1);
    CountDownLatch sendCompleted = new CountDownLatch(1);
    AtomicReference<Context> chunkContext = new AtomicReference<>();
    AtomicReference<Throwable> chunkFailure = new AtomicReference<>();
    AtomicReference<Throwable> sendFailure = new AtomicReference<>();
    AtomicInteger chunkCallbackCount = new AtomicInteger();
    Throwable abortCause = new IOException("aborted deferred upload");
    Callback callback =
        new Callback() {
          @Override
          public void succeeded() {
            chunkContext.set(Context.current());
            chunkCallbackCount.incrementAndGet();
            chunkCompleted.countDown();
          }

          @Override
          public void failed(Throwable failure) {
            chunkContext.set(Context.current());
            chunkFailure.set(failure);
            chunkCallbackCount.incrementAndGet();
            chunkCompleted.countDown();
          }
        };

    try {
      Request request =
          client
              .newRequest("http://localhost:" + server.getAddress().getPort() + "/")
              .method("POST")
              .content(contentProvider);
      try (Scope ignored = callerContext.makeCurrent()) {
        request.send(
            result -> {
              sendFailure.set(result.getFailure());
              sendCompleted.countDown();
            });
      }
      assertThat(requestStarted.await(10, SECONDS)).isTrue();

      try (Scope ignored = producerContext.makeCurrent()) {
        assertThat(contentProvider.offer(ByteBuffer.allocate(16 * 1024 * 1024), callback)).isTrue();
        contentProvider.close();
      }
      assertThat(contentStarted.await(10, SECONDS)).isTrue();
      request.abort(abortCause);

      assertThat(chunkCompleted.await(10, SECONDS)).isTrue();
      assertThat(sendCompleted.await(10, SECONDS)).isTrue();
      assertThat(chunkFailure.get()).isNotNull();
      assertThat(sendFailure.get()).isNotNull();
      assertThat(chunkCallbackCount.get()).isEqualTo(1);
      assertCallerContext(chunkContext.get(), callerContext);
    } finally {
      releaseServer.countDown();
      server.stop(0);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(SpanKind.CLIENT).hasNoParent()));
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

  private static byte[] readRequestBody(HttpExchange exchange) throws IOException {
    try (InputStream input = exchange.getRequestBody();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int length;
      while ((length = input.read(buffer)) != -1) {
        output.write(buffer, 0, length);
      }
      return output.toByteArray();
    }
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
