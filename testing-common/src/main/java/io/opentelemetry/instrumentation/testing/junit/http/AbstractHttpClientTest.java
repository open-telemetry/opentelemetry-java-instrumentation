/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttpClientTest<REQUEST> {
  static final String BASIC_AUTH_KEY = "custom-authorization-header";
  static final String BASIC_AUTH_VAL = "plain text auth token";

  /**
   * Build the request to be passed to {@link #sendRequest(java.lang.Object, java.lang.String,
   * java.net.URI, java.util.Map)}.
   *
   * <p>By splitting this step out separate from {@code sendRequest}, tests and re-execute the same
   * request a second time to verify that the traceparent header is not added multiple times to the
   * request, and that the last one wins. Tests will fail if the header shows multiple times.
   */
  protected abstract REQUEST buildRequest(String method, URI uri, Map<String, String> headers);

  /**
   * Helper class for capturing result of asynchronous request and running a callback when result is
   * received.
   */
  public static class RequestResult {
    private static final long timeout = 10_000;
    private final CountDownLatch valueReady = new CountDownLatch(1);
    private final Runnable callback;
    private int status;
    private Throwable throwable;

    public RequestResult(Runnable callback) {
      this.callback = callback;
    }

    public void complete(int status) {
      complete(() -> status, null);
    }

    public void complete(Throwable throwable) {
      complete(null, throwable);
    }

    public void complete(Supplier<Integer> status, Throwable throwable) {
      if (throwable != null) {
        this.throwable = throwable;
      } else {
        this.status = status.get();
      }
      callback.run();
      valueReady.countDown();
    }

    public int get() throws Throwable {
      if (!valueReady.await(timeout, TimeUnit.MILLISECONDS)) {
        throw new TimeoutException("Timed out waiting for response in " + timeout + "ms");
      }
      if (throwable != null) {
        throw throwable;
      }
      return status;
    }
  }

  /**
   * Make the request and return the status code of the response synchronously. Some clients, e.g.,
   * HTTPUrlConnection only support synchronous execution without callbacks, and many offer a
   * dedicated API for invoking synchronously, such as OkHttp's execute method.
   */
  protected abstract int sendRequest(
      REQUEST request, String method, URI uri, Map<String, String> headers) throws Exception;

  protected void sendRequestWithCallback(
      REQUEST request,
      String method,
      URI uri,
      Map<String, String> headers,
      RequestResult requestResult)
      throws Exception {
    // Must be implemented if testAsync is true
    throw new UnsupportedOperationException();
  }

  /** Returns the connection timeout that should be used when setting up tested clients. */
  protected final Duration connectTimeout() {
    return Duration.ofSeconds(5);
  }

  protected final Duration readTimeout() {
    return Duration.ofSeconds(2);
  }

  private InstrumentationTestRunner testing;
  private HttpClientTestServer server;

  private final HttpClientTestOptions options = new HttpClientTestOptions();

  @BeforeAll
  void setupOptions() {
    // TODO(anuraaga): Have subclasses configure options directly and remove mapping of legacy
    // protected methods.
    options.setHttpAttributes(this::httpAttributes);
    options.setExpectedClientSpanNameMapper(this::expectedClientSpanName);
    Integer responseCodeOnError = responseCodeOnRedirectError();
    if (responseCodeOnError != null) {
      options.setResponseCodeOnRedirectError(responseCodeOnError);
    }
    options.setUserAgent(userAgent());
    options.setClientSpanErrorMapper(this::clientSpanError);
    options.setSingleConnectionFactory(this::createSingleConnection);
    if (!testWithClientParent()) {
      options.disableTestWithClientParent();
    }
    if (!testRedirects()) {
      options.disableTestRedirects();
    }
    if (!testCircularRedirects()) {
      options.disableTestCircularRedirects();
    }
    options.setMaxRedirects(maxRedirects());
    if (!testReusedRequest()) {
      options.disableTestReusedRequest();
    }
    if (!testConnectionFailure()) {
      options.disableTestConnectionFailure();
    }
    if (testReadTimeout()) {
      options.enableTestReadTimeout();
    }
    if (!testRemoteConnection()) {
      options.disableTestRemoteConnection();
    }
    if (!testHttps()) {
      options.disableTestHttps();
    }
    if (!testCausality()) {
      options.disableTestCausality();
    }
    if (!testCausalityWithCallback()) {
      options.disableTestCausalityWithCallback();
    }
    if (!testCallback()) {
      options.disableTestCallback();
    }
    if (!testCallbackWithParent()) {
      options.disableTestCallbackWithParent();
    }
    if (!testErrorWithCallback()) {
      options.disableTestErrorWithCallback();
    }

    configure(options);
  }

  @BeforeEach
  void verifyExtension() {
    if (testing == null) {
      throw new AssertionError(
          "Subclasses of AbstractHttpClientTest must register either "
              + "HttpClientLibraryInstrumentationExtension or "
              + "HttpClientAgentInstrumentationExtension");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/success", "/success?with=params"})
  void successfulGetRequest(String path) throws Exception {
    URI uri = resolveAddress(path);
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "POST"})
  void successfulRequestWithParent(String method) throws Exception {
    URI uri = resolveAddress("/success");
    int responseCode = testing.runWithSpan("parent", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> assertClientSpan(span, uri, method, responseCode).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @Test
  void successfulRequestWithNotSampledParent() throws Exception {
    String method = "GET";
    URI uri = resolveAddress("/success");
    int responseCode = testing.runWithNonRecordingSpan(() -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    // sleep to ensure no spans are emitted
    Thread.sleep(200);

    assertThat(testing.traces()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "POST"})
  void shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan(String method)
      throws Exception {
    assumeTrue(options.testWithClientParent);

    URI uri = resolveAddress("/success");
    int responseCode =
        testing.runWithClientSpan("parent-client-span", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-client-span").hasKind(SpanKind.CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span)));
  }

  // FIXME: add tests for POST with large/chunked data

  @Test
  void requestWithCallbackAndParent() throws Throwable {
    assumeTrue(options.testCallback);
    assumeTrue(options.testCallbackWithParent);

    String method = "GET";
    URI uri = resolveAddress("/success");

    RequestResult result =
        testing.runWithSpan(
            "parent",
            () -> doRequestWithCallback(method, uri, () -> testing.runWithSpan("child", () -> {})));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> assertClientSpan(span, uri, method, 200).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)),
              span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void requestWithCallbackAndNoParent() throws Throwable {
    assumeTrue(options.testCallback);

    String method = "GET";
    URI uri = resolveAddress("/success");

    RequestResult result =
        doRequestWithCallback(method, uri, () -> testing.runWithSpan("callback", () -> {}));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          List<List<SpanData>> traces = testing.traces();
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void basicRequestWith1Redirect() throws Exception {
    // TODO quite a few clients create an extra span for the redirect
    // This test should handle both types or we should unify how the clients work

    assumeTrue(options.testRedirects);

    String method = "GET";
    URI uri = resolveAddress("/redirect");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void basicRequestWith2Redirects() throws Exception {
    // TODO quite a few clients create an extra span for the redirect
    // This test should handle both types or we should unify how the clients work

    assumeTrue(options.testRedirects);

    String method = "GET";
    URI uri = resolveAddress("/another-redirect");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void circularRedirects() {
    assumeTrue(options.testRedirects);
    assumeTrue(options.testCircularRedirects);

    String method = "GET";
    URI uri = resolveAddress("/circular-redirect");

    Throwable thrown = catchThrowable(() -> doRequest(method, uri));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(
              span ->
                  assertClientSpan(span, uri, method, options.responseCodeOnRedirectError)
                      .hasNoParent()
                      .hasException(clientError));
          for (int i = 0; i < options.maxRedirects; i++) {
            assertions.add(span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          }
          trace.hasSpansSatisfyingExactly(assertions.toArray(new Consumer[0]));
        });
  }

  @Test
  void redirectToSecuredCopiesAuthHeader() throws Exception {
    assumeTrue(options.testRedirects);

    String method = "GET";
    URI uri = resolveAddress("/to-secured");

    int responseCode =
        doRequest(method, uri, Collections.singletonMap(BASIC_AUTH_KEY, BASIC_AUTH_VAL));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void errorSpan() {
    String method = "GET";
    URI uri = resolveAddress("/error");

    testing.runWithSpan(
        "parent",
        () -> {
          try {
            doRequest(method, uri);
          } catch (Throwable ignored) {
          }
        });

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> assertClientSpan(span, uri, method, 500).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @Test
  void reuseRequest() throws Exception {
    assumeTrue(options.testReusedRequest);

    String method = "GET";
    URI uri = resolveAddress("/success");

    int responseCode = doReusedRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  // this test verifies two things:
  // * the javaagent doesn't cause multiples of tracing headers to be added
  //   (TestHttpServer throws exception if there are multiples)
  // * the javaagent overwrites the existing tracing headers
  //   (so that it propagates the same trace id / span id that it reports to the backend
  //   and the trace is not broken)
  @Test
  void requestWithExistingTracingHeaders() throws Exception {
    String method = "GET";
    URI uri = resolveAddress("/success");

    int responseCode = doRequestWithExistingTracingHeaders(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void connectionErrorUnopenedPort() {
    assumeTrue(options.testConnectionFailure);

    String method = "GET";
    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> doRequest(method, uri)));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("parent")
                      .hasKind(SpanKind.INTERNAL)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasException(ex),
              span ->
                  assertClientSpan(span, uri, method, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError));
        });
  }

  @Test
  void connectionErrorUnopenedPortWithCallback() throws Exception {
    assumeTrue(options.testConnectionFailure);
    assumeTrue(options.testCallback);
    assumeTrue(options.testErrorWithCallback);

    String method = "GET";
    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');

    RequestResult result =
        testing.runWithSpan(
            "parent",
            () ->
                doRequestWithCallback(
                    method, uri, () -> testing.runWithSpan("callback", () -> {})));

    Throwable thrown = catchThrowable(result::get);
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  assertClientSpan(span, uri, method, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError),
              span ->
                  span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void connectionErrorNonRoutableAddress() {
    assumeTrue(options.testRemoteConnection);

    String method = "HEAD";
    URI uri = URI.create(options.testHttps ? "https://192.0.2.1/" : "http://192.0.2.1/");

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> doRequest(method, uri)));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("parent")
                      .hasKind(SpanKind.INTERNAL)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasException(ex),
              span ->
                  assertClientSpan(span, uri, method, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError));
        });
  }

  @Test
  void readTimedOut() {
    assumeTrue(options.testReadTimeout);

    String method = "GET";
    URI uri = resolveAddress("/read-timeout");

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> doRequest(method, uri)));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("parent")
                      .hasKind(SpanKind.INTERNAL)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasException(ex),
              span ->
                  assertClientSpan(span, uri, method, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @DisabledIfSystemProperty(
      named = "java.vm.name",
      matches = ".*IBM J9 VM.*",
      disabledReason = "IBM JVM has different protocol support for TLS")
  @Test
  void httpsRequest() throws Exception {
    assumeTrue(options.testRemoteConnection);
    assumeTrue(options.testHttps);

    String method = "GET";
    URI uri = URI.create("https://localhost:" + server.httpsPort() + "/success");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  /**
   * This test fires a large number of concurrent requests. Each request first hits a HTTP server
   * and then makes another client request. The goal of this test is to verify that in highly
   * concurrent environment our instrumentations for http clients (especially inherently concurrent
   * ones, such as Netty or Reactor) correctly propagate trace context.
   */
  @Test
  void highConcurrency() {
    assumeTrue(options.testCausality);

    int count = 50;
    String method = "GET";
    URI uri = resolveAddress("/success");

    CountDownLatch latch = new CountDownLatch(1);

    ExecutorService pool = Executors.newFixedThreadPool(4);
    for (int i = 0; i < count; i++) {
      int index = i;
      Runnable job =
          () -> {
            try {
              latch.await();
            } catch (InterruptedException e) {
              throw new AssertionError(e);
            }
            try {
              testing.runWithSpan(
                  "Parent span " + index,
                  () -> {
                    Span.current().setAttribute("test.request.id", index);
                    doRequest(
                        method,
                        uri,
                        Collections.singletonMap("test-request-id", String.valueOf(index)));
                  });
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          };
      pool.submit(job);
    }
    latch.countDown();

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      assertions.add(
          trace -> {
            SpanData rootSpan = trace.getSpan(0);
            // Traces can be in arbitrary order, let us find out the request id of the current one
            int requestId = Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(rootSpan.getName())
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            attrs -> assertThat(attrs).containsEntry("test.request.id", requestId)),
                span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs).containsEntry("test.request.id", requestId)));
          });
    }

    testing.waitAndAssertTraces(assertions);

    pool.shutdown();
  }

  @Test
  void highConcurrencyWithCallback() {
    assumeTrue(options.testCausality);
    assumeTrue(options.testCausalityWithCallback);
    assumeTrue(options.testCallback);
    assumeTrue(options.testCallbackWithParent);

    int count = 50;
    String method = "GET";
    URI uri = resolveAddress("/success");

    CountDownLatch latch = new CountDownLatch(1);

    ExecutorService pool = Executors.newFixedThreadPool(4);
    IntStream.range(0, count)
        .forEach(
            index -> {
              Runnable job =
                  () -> {
                    try {
                      latch.await();
                    } catch (InterruptedException e) {
                      throw new AssertionError(e);
                    }
                    try {
                      RequestResult result =
                          testing.runWithSpan(
                              "Parent span " + index,
                              () -> {
                                Span.current().setAttribute("test.request.id", index);
                                return doRequestWithCallback(
                                    method,
                                    uri,
                                    Collections.singletonMap(
                                        "test-request-id", String.valueOf(index)),
                                    () -> testing.runWithSpan("child", () -> {}));
                              });
                      assertThat(result.get()).isEqualTo(200);
                    } catch (Throwable throwable) {
                      if (throwable instanceof AssertionError) {
                        throw (AssertionError) throwable;
                      }
                      throw new AssertionError(throwable);
                    }
                  };
              pool.submit(job);
            });
    latch.countDown();

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      assertions.add(
          trace -> {
            SpanData rootSpan = trace.getSpan(0);
            // Traces can be in arbitrary order, let us find out the request id of the current one
            int requestId = Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(rootSpan.getName())
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            attrs -> assertThat(attrs).containsEntry("test.request.id", requestId)),
                span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            attrs -> assertThat(attrs).containsEntry("test.request.id", requestId)),
                span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(rootSpan));
          });
    }

    testing.waitAndAssertTraces(assertions);

    pool.shutdown();
  }

  /**
   * Almost similar to the "high concurrency test" test above, but all requests use the same single
   * connection.
   */
  @Test
  void highConcurrencyOnSingleConnection() {
    SingleConnection singleConnection =
        options.singleConnectionFactory.apply("localhost", server.httpPort());
    assumeTrue(singleConnection != null);

    int count = 50;
    String method = "GET";
    String path = "/success";
    URI uri = resolveAddress(path);

    CountDownLatch latch = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    for (int i = 0; i < count; i++) {
      int index = i;
      Runnable job =
          () -> {
            try {
              latch.await();
            } catch (InterruptedException e) {
              throw new AssertionError(e);
            }
            testing.runWithSpan(
                "Parent span " + index,
                () -> {
                  Span.current().setAttribute("test.request.id", index);
                  try {
                    singleConnection.doRequest(
                        path, Collections.singletonMap("test-request-id", String.valueOf(index)));
                  } catch (InterruptedException e) {
                    throw new AssertionError(e);
                  } catch (Exception e) {
                    throw new AssertionError(e);
                  }
                });
          };
      pool.submit(job);
    }
    latch.countDown();

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      assertions.add(
          trace -> {
            SpanData rootSpan = trace.getSpan(0);
            // Traces can be in arbitrary order, let us find out the request id of the current one
            int requestId = Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(rootSpan.getName())
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            attrs -> assertThat(attrs).containsEntry("test.request.id", requestId)),
                span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs).containsEntry("test.request.id", requestId)));
          });
    }

    testing.waitAndAssertTraces(assertions);

    pool.shutdown();
  }

  // Visible for spock bridge.
  SpanDataAssert assertClientSpan(
      SpanDataAssert span, URI uri, String method, Integer responseCode) {
    Set<AttributeKey<?>> httpClientAttributes = options.httpAttributes.apply(uri);
    return span.hasName(options.expectedClientSpanNameMapper.apply(uri, method))
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            attrs -> {
              if (uri.getPort() == PortUtils.UNUSABLE_PORT || uri.getHost().equals("192.0.2.1")) {
                // TODO(anuraaga): For theses cases, there isn't actually a peer so we shouldn't be
                // filling in peer information but some instrumentation does so based on the URL
                // itself which is present in HTTP attributes. We should fix this.
                if (attrs.asMap().containsKey(SemanticAttributes.NET_PEER_NAME)) {
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_NAME, uri.getHost());
                }
                if (attrs.asMap().containsKey(SemanticAttributes.NET_PEER_PORT)) {
                  if (uri.getPort() > 0) {
                    assertThat(attrs)
                        .containsEntry(SemanticAttributes.NET_PEER_PORT, (long) uri.getPort());
                  } else {
                    // https://192.0.2.1/ where some instrumentation may have set this to 443, but
                    // not all.
                    assertThat(attrs)
                        .hasEntrySatisfying(
                            SemanticAttributes.NET_PEER_PORT,
                            port -> {
                              // Some instrumentation seem to set NET_PEER_PORT to -1 incorrectly.
                              if (port > 0) {
                                assertThat(port).isEqualTo(options.testHttps ? 443 : 80);
                              }
                            });
                  }
                }
              } else {
                if (httpClientAttributes.contains(SemanticAttributes.NET_PEER_NAME)) {
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_NAME, uri.getHost());
                }
                if (httpClientAttributes.contains(SemanticAttributes.NET_PEER_PORT)) {
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_PORT, uri.getPort());
                }
              }

              // Optional
              // TODO(anuraaga): Move to test knob rather than always treating
              // as optional
              if (attrs.asMap().containsKey(SemanticAttributes.NET_PEER_IP)) {
                if (uri.getHost().equals("192.0.2.1")) {
                  // NB(anuraaga): This branch seems to currently only be exercised on Java 15.
                  // It would be good to understand how the JVM version is impacting this check.
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_IP, "192.0.2.1");
                } else {
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_IP, "127.0.0.1");
                }
              }

              if (httpClientAttributes.contains(SemanticAttributes.HTTP_URL)) {
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_URL, uri.toString());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_METHOD)) {
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_METHOD, method);
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_FLAVOR)) {
                // TODO(anuraaga): Support HTTP/2
                assertThat(attrs)
                    .containsEntry(
                        SemanticAttributes.HTTP_FLAVOR,
                        SemanticAttributes.HttpFlavorValues.HTTP_1_1);
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_USER_AGENT)) {
                String userAgent = options.userAgent;
                if (userAgent != null) {
                  assertThat(attrs)
                      .hasEntrySatisfying(
                          SemanticAttributes.HTTP_USER_AGENT,
                          actual -> assertThat(actual).startsWith(userAgent));
                }
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                        length -> assertThat(length).isNotNegative());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        length -> assertThat(length).isNotNegative());
              }

              if (responseCode != null) {
                assertThat(attrs)
                    .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, (long) responseCode);
              } else {
                // worth adding AttributesAssert.doesNotContainKey?
                assertThat(attrs.get(SemanticAttributes.HTTP_STATUS_CODE)).isNull();
              }
            });
  }

  // Visible for spock bridge.
  static SpanDataAssert assertServerSpan(SpanDataAssert span) {
    return span.hasName("test-http-server").hasKind(SpanKind.SERVER);
  }

  protected Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>();
    attributes.add(SemanticAttributes.HTTP_URL);
    attributes.add(SemanticAttributes.HTTP_METHOD);
    attributes.add(SemanticAttributes.HTTP_FLAVOR);
    attributes.add(SemanticAttributes.HTTP_USER_AGENT);
    return attributes;
  }

  protected String expectedClientSpanName(URI uri, String method) {
    return method != null ? "HTTP " + method : "HTTP request";
  }

  @Nullable
  protected Integer responseCodeOnRedirectError() {
    return null;
  }

  @Nullable
  protected String userAgent() {
    return null;
  }

  protected Throwable clientSpanError(URI uri, Throwable exception) {
    return exception;
  }

  // This method should create either a single connection to the target uri or a http client
  // which is guaranteed to use the same connection for all requests
  @Nullable
  protected SingleConnection createSingleConnection(String host, int port) {
    return null;
  }

  protected boolean testWithClientParent() {
    return true;
  }

  protected boolean testRedirects() {
    return true;
  }

  protected boolean testCircularRedirects() {
    return true;
  }

  // maximum number of redirects that http client follows before giving up
  protected int maxRedirects() {
    return 2;
  }

  protected boolean testReusedRequest() {
    return true;
  }

  protected boolean testConnectionFailure() {
    return true;
  }

  protected boolean testReadTimeout() {
    return false;
  }

  protected boolean testRemoteConnection() {
    return true;
  }

  protected boolean testHttps() {
    return true;
  }

  protected boolean testCausality() {
    return true;
  }

  protected boolean testCausalityWithCallback() {
    return true;
  }

  protected boolean testCallback() {
    return true;
  }

  protected boolean testCallbackWithParent() {
    // FIXME: this hack is here because callback with parent is broken in play-ws when the stream()
    // function is used.  There is no way to stop a test from a derived class hence the flag
    return true;
  }

  protected boolean testErrorWithCallback() {
    return true;
  }

  protected void configure(HttpClientTestOptions options) {}

  private int doRequest(String method, URI uri) throws Exception {
    return doRequest(method, uri, Collections.emptyMap());
  }

  private int doRequest(String method, URI uri, Map<String, String> headers) throws Exception {
    REQUEST request = buildRequest(method, uri, headers);
    return sendRequest(request, method, uri, headers);
  }

  private int doReusedRequest(String method, URI uri) throws Exception {
    REQUEST request = buildRequest(method, uri, Collections.emptyMap());
    sendRequest(request, method, uri, Collections.emptyMap());
    return sendRequest(request, method, uri, Collections.emptyMap());
  }

  private int doRequestWithExistingTracingHeaders(String method, URI uri) throws Exception {
    Map<String, String> headers = new HashMap();
    for (String field :
        testing.getOpenTelemetry().getPropagators().getTextMapPropagator().fields()) {
      headers.put(field, "12345789");
    }
    REQUEST request = buildRequest(method, uri, headers);
    return sendRequest(request, method, uri, headers);
  }

  private RequestResult doRequestWithCallback(String method, URI uri, Runnable callback)
      throws Exception {
    return doRequestWithCallback(method, uri, Collections.emptyMap(), callback);
  }

  private RequestResult doRequestWithCallback(
      String method, URI uri, Map<String, String> headers, Runnable callback) throws Exception {
    REQUEST request = buildRequest(method, uri, headers);
    RequestResult requestResult = new RequestResult(callback);
    sendRequestWithCallback(request, method, uri, headers, requestResult);
    return requestResult;
  }

  protected URI resolveAddress(String path) {
    return URI.create("http://localhost:" + server.httpPort() + path);
  }

  final void setTesting(InstrumentationTestRunner testing, HttpClientTestServer server) {
    this.testing = testing;
    this.server = server;
  }
}
