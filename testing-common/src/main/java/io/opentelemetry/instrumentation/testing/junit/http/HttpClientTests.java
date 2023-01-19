/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.DynamicTest;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.junit.Assume.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public final class HttpClientTests<REQUEST> {

  public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);
  public static final long CONNECTION_TIMEOUT_MS = CONNECTION_TIMEOUT.toMillis();
  public static final Duration READ_TIMEOUT = Duration.ofSeconds(2);
  public static final long READ_TIMEOUT_MS = READ_TIMEOUT.toMillis();

  static final String BASIC_AUTH_KEY = "custom-authorization-header";
  static final String BASIC_AUTH_VAL = "plain text auth token";

  private final InstrumentationTestRunner testRunner;
  private final HttpClientTestServer server;

  private final HttpClientTestOptions options;
  private final HttpClientTypeAdapter<REQUEST> clientAdapter;

  public HttpClientTests(InstrumentationTestRunner testRunner, HttpClientTestServer server,
      HttpClientTestOptions options, HttpClientTypeAdapter<REQUEST> clientAdapter) {
    this.testRunner = testRunner;
    this.server = server;
    this.options = options;
    this.clientAdapter = clientAdapter;
  }

  public List<DynamicTest> all(){
    return Stream.of(
            successfulRequestWithNotSampledParent(),
            requestWithCallbackAndParent(),
            basicRequestWith1Redirect(),
            basicRequestWith2Redirects()
        )
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public DynamicTest successfulRequestWithNotSampledParent() {
    return dynamicTest("successful request with not sampled parent", () -> {
      String method = "GET";
      URI uri = resolveAddress("/success");
      int responseCode = testRunner.runWithNonRecordingSpan(() -> doRequest(method, uri));

      assertThat(responseCode).isEqualTo(200);

      // sleep to ensure no spans are emitted
      Thread.sleep(200);

      assertThat(testRunner.traces()).isEmpty();
    });
  }

  // FIXME: add tests for POST with large/chunked data

  DynamicTest requestWithCallbackAndParent() {
    if(!options.testCallback || !options.testCallbackWithParent){
      return null;
    }
    return dynamicTest("request with callback and parent", () -> {
      String method = "GET";
      URI uri = resolveAddress("/success");

      HttpClientResult result =
          testRunner.runWithSpan(
              "parent",
              () -> doRequestWithCallback(method, uri, () -> testRunner.runWithSpan("child", () -> {})));

      assertThat(result.get()).isEqualTo(200);

      testRunner.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertClientSpan(span, uri, method, 200).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(1)),
                span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
          });
    });
  }

  DynamicTest basicRequestWith1Redirect() {
    if (!options.testRedirects) {
      return null;
    }
    return dynamicTest("basic request with 1 redirect", () -> {
      // TODO quite a few clients create an extra span for the redirect
      // This test should handle both types or we should unify how the clients work

      String method = "GET";
      URI uri = resolveAddress("/redirect");

      int responseCode = doRequest(method, uri);

      assertThat(responseCode).isEqualTo(200);

      testRunner.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    });
  }

  DynamicTest basicRequestWith2Redirects() {
    if(!options.testRedirects){
     return null;
    }

    return dynamicTest("basic request with 2 redirects", () -> {
      // TODO quite a few clients create an extra span for the redirect
      // This test should handle both types or we should unify how the clients work

      String method = "GET";
      URI uri = resolveAddress("/another-redirect");

      int responseCode = doRequest(method, uri);

      assertThat(responseCode).isEqualTo(200);

      testRunner.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    });
  }


/*
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

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "POST"})
  void shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan(String method)
      throws Exception {
    assumeTrue(options.testWithClientParent);

    URI uri = resolveAddress("/success");
    int responseCode =
        testing.runWithHttpClientSpan("parent-client-span", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-client-span").hasKind(SpanKind.CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span)));
  }

  @Test
  void requestWithCallbackAndNoParent() throws Throwable {
    assumeTrue(options.testCallback);
    assumeFalse(options.testCallbackWithImplicitParent);

    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result =
        doRequestWithCallback(method, uri, () -> testing.runWithSpan("callback", () -> {}));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void requestWithCallbackAndImplicitParent() throws Throwable {
    assumeTrue(options.testCallbackWithImplicitParent);

    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result =
        doRequestWithCallback(method, uri, () -> testing.runWithSpan("callback", () -> {}));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
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
          trace.hasSpansSatisfyingExactly(assertions);
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

    HttpClientResult result =
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
          List<Consumer<SpanDataAssert>> spanAsserts =
              Arrays.asList(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertClientSpan(span, uri, method, null)
                          .hasParent(trace.getSpan(0))
                          .hasException(clientError),
                  span ->
                      span.hasName("callback")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0)));
          boolean jdk8 = Objects.equals(System.getProperty("java.specification.version"), "1.8");
          if (jdk8) {
            // on some netty based http clients order of `CONNECT` and `callback` spans isn't
            // guaranteed when running on jdk8
            trace.hasSpansSatisfyingExactlyInAnyOrder(spanAsserts);
          } else {
            trace.hasSpansSatisfyingExactly(spanAsserts);
          }
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
*/

  /**
   * This test fires a large number of concurrent requests. Each request first hits an HTTP server
   * and then makes another client request. The goal of this test is to verify that in highly
   * concurrent environment our instrumentations for http clients (especially inherently concurrent
   * ones, such as Netty or Reactor) correctly propagate trace context.
   */
  /*
  @Test
  void highConcurrency() {
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
              Integer result =
                  testing.runWithSpan(
                      "Parent span " + index,
                      () -> {
                        Span.current().setAttribute("test.request.id", index);
                        return doRequest(
                            method,
                            uri,
                            Collections.singletonMap("test-request-id", String.valueOf(index)));
                      });
              assertThat(result).isEqualTo(200);
            } catch (Throwable throwable) {
              if (throwable instanceof AssertionError) {
                throw (AssertionError) throwable;
              }
              throw new AssertionError(throwable);
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
                      HttpClientResult result =
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
*/

  /**
   * Almost similar to the "high concurrency test" test above, but all requests use the same single
   * connection.
   */
  /*
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
            try {
              Integer result =
                  testing.runWithSpan(
                      "Parent span " + index,
                      () -> {
                        Span.current().setAttribute("test.request.id", index);
                        return singleConnection.doRequest(
                            path,
                            Collections.singletonMap("test-request-id", String.valueOf(index)));
                      });
              assertThat(result).isEqualTo(200);
            } catch (Throwable throwable) {
              if (throwable instanceof AssertionError) {
                throw (AssertionError) throwable;
              }
              throw new AssertionError(throwable);
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
*/
  // Visible for spock bridge.
  SpanDataAssert assertClientSpan(
      SpanDataAssert span, URI uri, String method, Integer responseCode) {
    Set<AttributeKey<?>> httpClientAttributes = options.httpAttributes.apply(uri);
    return span.hasName(options.expectedClientSpanNameMapper.apply(uri, method))
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            attrs -> {
              // TODO: Move to test knob rather than always treating as optional
              if (attrs.get(SemanticAttributes.NET_TRANSPORT) != null) {
                assertThat(attrs).containsEntry(SemanticAttributes.NET_TRANSPORT, IP_TCP);
              }
              if (httpClientAttributes.contains(SemanticAttributes.NET_PEER_NAME)) {
                assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_NAME, uri.getHost());
              }
              if (httpClientAttributes.contains(SemanticAttributes.NET_PEER_PORT)) {
                int uriPort = uri.getPort();
                // default values are ignored
                if (uriPort <= 0 || uriPort == 80 || uriPort == 443) {
                  assertThat(attrs).doesNotContainKey(SemanticAttributes.NET_PEER_PORT);
                } else {
                  assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_PORT, uriPort);
                }
              }

              if (uri.getPort() == PortUtils.UNUSABLE_PORT || uri.getHost().equals("192.0.2.1")) {
                // In these cases the peer connection is not established, so the HTTP client should
                // not report any socket-level attributes
                assertThat(attrs)
                    .doesNotContainKey("net.sock.family")
                    // TODO netty sometimes reports net.sock.peer.addr in connection error test
                    // .doesNotContainKey("net.sock.peer.addr")
                    .doesNotContainKey("net.sock.peer.name")
                    .doesNotContainKey("net.sock.peer.port");

              } else {
                // TODO: Move to test knob rather than always treating as optional
                if (attrs.get(SemanticAttributes.NET_SOCK_PEER_ADDR) != null) {
                  assertThat(attrs)
                      .containsEntry(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1");
                }
                if (attrs.get(SemanticAttributes.NET_SOCK_PEER_PORT) != null) {
                  assertThat(attrs)
                      .containsEntry(
                          SemanticAttributes.NET_SOCK_PEER_PORT,
                          Objects.equals(uri.getScheme(), "https")
                              ? server.httpsPort()
                              : server.httpPort());
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
              if (attrs.get(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH) != null) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                        length -> assertThat(length).isNotNegative());
              }
              if (attrs.get(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH) != null) {
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

  /*
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

  protected boolean testCallback() {
    return true;
  }

  protected boolean testCallbackWithParent() {
    // FIXME: this hack is here because callback with parent is broken in play-ws when the stream()
    // function is used.  There is no way to stop a test from a derived class hence the flag
    return true;
  }

  protected boolean testCallbackWithImplicitParent() {
    // depending on async behavior callback can be executed within
    // parent span scope or outside of the scope, e.g. in reactor-netty or spring
    // callback is correlated.
    return false;
  }

  protected boolean testErrorWithCallback() {
    return true;
  }

  protected void configure(LegacyHttpClientTestOptions options) {}
*/

  private int doRequest(String method, URI uri) throws Exception {
    return doRequest(method, uri, Collections.emptyMap());
  }

  private int doRequest(String method, URI uri, Map<String, String> headers) throws Exception {
    REQUEST request = clientAdapter.buildRequest(method, uri, headers);
    return clientAdapter.sendRequest(request, method, uri, headers);
  }

  private int doReusedRequest(String method, URI uri) throws Exception {
    REQUEST request = clientAdapter.buildRequest(method, uri, Collections.emptyMap());
    clientAdapter.sendRequest(request, method, uri, Collections.emptyMap());
    return clientAdapter.sendRequest(request, method, uri, Collections.emptyMap());
  }

  private int doRequestWithExistingTracingHeaders(String method, URI uri) throws Exception {
    Map<String, String> headers = new HashMap<>();
    for (String field :
        testRunner.getOpenTelemetry().getPropagators().getTextMapPropagator().fields()) {
      headers.put(field, "12345789");
    }
    REQUEST request = clientAdapter.buildRequest(method, uri, headers);
    return clientAdapter.sendRequest(request, method, uri, headers);
  }

  private HttpClientResult doRequestWithCallback(String method, URI uri, Runnable callback)
      throws Exception {
    return doRequestWithCallback(method, uri, Collections.emptyMap(), callback);
  }

  private HttpClientResult doRequestWithCallback(
      String method, URI uri, Map<String, String> headers, Runnable callback) throws Exception {
    REQUEST request = clientAdapter.buildRequest(method, uri, headers);
    HttpClientResult httpClientResult = new HttpClientResult(callback);
    clientAdapter.sendRequestWithCallback(request, method, uri, headers, httpClientResult);
    return httpClientResult;
  }

  protected URI resolveAddress(String path) {
    return URI.create("http://localhost:" + server.httpPort() + path);
  }
//
//  final void setTesting(InstrumentationTestRunner testing, HttpClientTestServer server) {
//    this.testRunner = testing;
//    this.server = server;
//  }
}
