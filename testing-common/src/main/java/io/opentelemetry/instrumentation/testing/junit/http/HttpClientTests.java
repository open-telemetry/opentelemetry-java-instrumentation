/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

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

  public HttpClientTests(
      InstrumentationTestRunner testRunner,
      HttpClientTestServer server,
      HttpClientTestOptions options,
      HttpClientTypeAdapter<REQUEST> clientAdapter) {
    this.testRunner = testRunner;
    this.server = server;
    this.options = options;
    this.clientAdapter = clientAdapter;
  }

  public List<DynamicTest> allList() {
    return all().collect(Collectors.toList());
  }

  public Stream<DynamicTest> all() {
    // TODO: This is long and somewhat redundant.
    // TODO: Consider reflection to get all methods that take no args and return a dynamic test
    return Stream.of(
            successfulRequestWithNotSampledParent(),
            requestWithCallbackAndParent(),
            basicRequestWith1Redirect(),
            basicRequestWith2Redirects(),
            requestWithCallbackAndNoParent(),
            requestWithCallbackAndImplicitParent(),
            circularRedirects(),
            redirectToSecuredCopiesAuthHeader(),
            errorSpan(),
            reuseRequest(),
            requestWithExistingTracingHeaders(),
            connectionErrorUnopenedPort(),
            connectionErrorUnopenedPortWithCallback(),
            connectionErrorNonRoutableAddress(),
            readTimedOut(),
            highConcurrency(),
            highConcurrencyWithCallback(),
            highConcurrencyOnSingleConnection(),
            httpsRequest(),
            successfulGetRequest("/success"),
            successfulGetRequest("/success?with=params"),
            successfulRequestWithParent("PUT"),
            successfulRequestWithParent("POST"),
            shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan("PUT"),
            shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan("POST"))
        .filter(Objects::nonNull);
  }

  DynamicTest successfulRequestWithNotSampledParent() {
    return test(
        "successful request with not sampled parent",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");
          int responseCode = testRunner.runWithNonRecordingSpan(() -> doRequest(method, uri));

          assertThat(responseCode).isEqualTo(200);

          // sleep to ensure no spans are emitted
          Thread.sleep(200);

          assertThat(testRunner.traces()).isEmpty();
        });
  }

  // FIXME: add tests for POST with large/chunked data
  DynamicTest requestWithCallbackAndParent() {
    if (!options.testCallback || !options.testCallbackWithParent) {
      return null;
    }
    return test(
        "request with callback and parent",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");

          HttpClientResult result =
              testRunner.runWithSpan(
                  "parent",
                  () ->
                      doRequestWithCallback(
                          method, uri, () -> testRunner.runWithSpan("child", () -> {})));

          assertThat(result.get()).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                      span -> assertClientSpan(span, uri, method, 200).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(1)),
                      span ->
                          span.hasName("child")
                              .hasKind(SpanKind.INTERNAL)
                              .hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest basicRequestWith1Redirect() {
    if (!options.testRedirects) {
      return null;
    }
    return test(
        "basic request with 1 redirect",
        () -> {
          // TODO quite a few clients create an extra span for the redirect
          // This test should handle both types or we should unify how the clients work

          String method = "GET";
          URI uri = server.resolveAddress("/redirect");

          int responseCode = doRequest(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest basicRequestWith2Redirects() {
    if (!options.testRedirects) {
      return null;
    }

    return test(
        "basic request with 2 redirects",
        () -> {
          // TODO quite a few clients create an extra span for the redirect
          // This test should handle both types or we should unify how the clients work

          String method = "GET";
          URI uri = server.resolveAddress("/another-redirect");

          int responseCode = doRequest(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest requestWithCallbackAndNoParent() {
    if (!options.testCallback || options.testCallbackWithImplicitParent) {
      return null;
    }
    return test(
        "request with callback and no parent",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");

          HttpClientResult result =
              doRequestWithCallback(
                  method, uri, () -> testRunner.runWithSpan("callback", () -> {}));

          assertThat(result.get()).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
        });
  }

  DynamicTest requestWithCallbackAndImplicitParent() {
    if (!options.testCallbackWithImplicitParent) {
      return null;
    }
    return test(
        "request with callback and implicit parent",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");

          HttpClientResult result =
              doRequestWithCallback(
                  method, uri, () -> testRunner.runWithSpan("callback", () -> {}));

          assertThat(result.get()).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                      span ->
                          span.hasName("callback")
                              .hasKind(SpanKind.INTERNAL)
                              .hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest circularRedirects() {
    if (!options.testRedirects || !options.testCircularRedirects) {
      return null;
    }

    return test(
        "circular redirects",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/circular-redirect");

          Throwable thrown = catchThrowable(() -> doRequest(method, uri));
          Throwable ex;
          if (thrown instanceof ExecutionException) {
            ex = thrown.getCause();
          } else {
            ex = thrown;
          }
          Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

          testRunner.waitAndAssertTraces(
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
        });
  }

  DynamicTest redirectToSecuredCopiesAuthHeader() {
    if (!options.testRedirects) {
      return null;
    }
    return test(
        "redirect to secured copies auth header",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/to-secured");

          int responseCode =
              doRequest(method, uri, Collections.singletonMap(BASIC_AUTH_KEY, BASIC_AUTH_VAL));

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, 200).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest errorSpan() {
    return test(
        "error span",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/error");

          testRunner.runWithSpan(
              "parent",
              () -> {
                try {
                  doRequest(method, uri);
                } catch (Throwable expected) {
                }
              });

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                      span -> assertClientSpan(span, uri, method, 500).hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(1))));
        });
  }

  DynamicTest reuseRequest() {
    if (!options.testReusedRequest) {
      return null;
    }
    return test(
        "reuse request",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");

          int responseCode = doReusedRequest(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  // this test verifies two things:
  // * the javaagent doesn't cause multiples of tracing headers to be added
  //   (TestHttpServer throws exception if there are multiples)
  // * the javaagent overwrites the existing tracing headers
  //   (so that it propagates the same trace id / span id that it reports to the backend
  //   and the trace is not broken)
  DynamicTest requestWithExistingTracingHeaders() {
    return test(
        "request with existing tracing headers",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/success");

          int responseCode = doRequestWithExistingTracingHeaders(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest connectionErrorUnopenedPort() {
    if (!options.testConnectionFailure) {
      return null;
    }
    return test(
        "connection error on unopened port",
        () -> {
          String method = "GET";
          URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');

          Throwable thrown =
              catchThrowable(() -> testRunner.runWithSpan("parent", () -> doRequest(method, uri)));
          Throwable ex;
          if (thrown instanceof ExecutionException) {
            ex = thrown.getCause();
          } else {
            ex = thrown;
          }
          Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

          testRunner.waitAndAssertTraces(
              trace ->
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
                              .hasException(clientError)));
        });
  }

  DynamicTest connectionErrorUnopenedPortWithCallback() {
    if (!options.testConnectionFailure || !options.testCallback || !options.testErrorWithCallback) {
      return null;
    }

    return test(
        "connection error on unopened port with callback",
        () -> {
          String method = "GET";
          URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');

          HttpClientResult result =
              testRunner.runWithSpan(
                  "parent",
                  () ->
                      doRequestWithCallback(
                          method, uri, () -> testRunner.runWithSpan("callback", () -> {})));

          Throwable thrown = catchThrowable(result::get);
          Throwable ex;
          if (thrown instanceof ExecutionException) {
            ex = thrown.getCause();
          } else {
            ex = thrown;
          }
          Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

          testRunner.waitAndAssertTraces(
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
                boolean jdk8 =
                    Objects.equals(System.getProperty("java.specification.version"), "1.8");
                if (jdk8) {
                  // on some netty based http clients order of `CONNECT` and `callback` spans isn't
                  // guaranteed when running on jdk8
                  trace.hasSpansSatisfyingExactlyInAnyOrder(spanAsserts);
                } else {
                  trace.hasSpansSatisfyingExactly(spanAsserts);
                }
              });
        });
  }

  DynamicTest connectionErrorNonRoutableAddress() {
    if (!options.testRemoteConnection) {
      return null;
    }

    return test(
        "connection error for non routable address",
        () -> {
          String method = "HEAD";
          URI uri = URI.create(options.testHttps ? "https://192.0.2.1/" : "http://192.0.2.1/");

          Throwable thrown =
              catchThrowable(() -> testRunner.runWithSpan("parent", () -> doRequest(method, uri)));
          Throwable ex;
          if (thrown instanceof ExecutionException) {
            ex = thrown.getCause();
          } else {
            ex = thrown;
          }
          Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

          testRunner.waitAndAssertTraces(
              trace ->
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
                              .hasException(clientError)));
        });
  }

  DynamicTest readTimedOut() {
    if (!options.testReadTimeout) {
      return null;
    }
    return test(
        "read timed out",
        () -> {
          String method = "GET";
          URI uri = server.resolveAddress("/read-timeout");

          Throwable thrown =
              catchThrowable(() -> testRunner.runWithSpan("parent", () -> doRequest(method, uri)));
          Throwable ex;
          if (thrown instanceof ExecutionException) {
            ex = thrown.getCause();
          } else {
            ex = thrown;
          }
          Throwable clientError = options.clientSpanErrorMapper.apply(uri, ex);

          testRunner.waitAndAssertTraces(
              trace ->
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
                      span -> assertServerSpan(span).hasParent(trace.getSpan(1))));
        });
  }

  /**
   * This test fires a large number of concurrent requests. Each request first hits an HTTP server
   * and then makes another client request. The goal of this test is to verify that in highly
   * concurrent environment our instrumentations for http clients (especially inherently concurrent
   * ones, such as Netty or Reactor) correctly propagate trace context.
   */
  DynamicTest highConcurrency() {
    return test(
        "high concurrency",
        () -> {
          int count = 50;
          String method = "GET";
          URI uri = server.resolveAddress("/success");

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
                        testRunner.runWithSpan(
                            "Parent span " + index,
                            () -> {
                              Span.current().setAttribute("test.request.id", index);
                              return doRequest(
                                  method,
                                  uri,
                                  Collections.singletonMap(
                                      "test-request-id", String.valueOf(index)));
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
                  // Traces can be in arbitrary order, let us find out the request id of the current
                  // one
                  int requestId =
                      Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(rootSpan.getName())
                              .hasKind(SpanKind.INTERNAL)
                              .hasNoParent()
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)),
                      span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                      span ->
                          assertServerSpan(span)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)));
                });
          }

          testRunner.waitAndAssertTraces(assertions);

          pool.shutdown();
        });
  }

  DynamicTest highConcurrencyWithCallback() {
    if (!options.testCallback || !options.testCallbackWithParent) {
      return null;
    }
    return test(
        "high concurrency with callback",
        () -> {
          int count = 50;
          String method = "GET";
          URI uri = server.resolveAddress("/success");

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
                                testRunner.runWithSpan(
                                    "Parent span " + index,
                                    () -> {
                                      Span.current().setAttribute("test.request.id", index);
                                      return doRequestWithCallback(
                                          method,
                                          uri,
                                          Collections.singletonMap(
                                              "test-request-id", String.valueOf(index)),
                                          () -> testRunner.runWithSpan("child", () -> {}));
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
                  // Traces can be in arbitrary order, let us find out the request id of the current
                  // one
                  int requestId =
                      Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(rootSpan.getName())
                              .hasKind(SpanKind.INTERNAL)
                              .hasNoParent()
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)),
                      span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                      span ->
                          assertServerSpan(span)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)),
                      span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(rootSpan));
                });
          }

          testRunner.waitAndAssertTraces(assertions);

          pool.shutdown();
        });
  }

  /**
   * Almost similar to the "high concurrency test" test above, but all requests use the same single
   * connection.
   */
  DynamicTest highConcurrencyOnSingleConnection() {
    SingleConnection singleConnection =
        options.singleConnectionFactory.apply("localhost", server.httpPort());
    if (singleConnection == null) {
      return null;
    }
    return test(
        "high concurrency on single connection",
        () -> {
          int count = 50;
          String method = "GET";
          String path = "/success";
          URI uri = server.resolveAddress(path);

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
                        testRunner.runWithSpan(
                            "Parent span " + index,
                            () -> {
                              Span.current().setAttribute("test.request.id", index);
                              return singleConnection.doRequest(
                                  path,
                                  Collections.singletonMap(
                                      "test-request-id", String.valueOf(index)));
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
                  // Traces can be in arbitrary order, let us find out the request id of the current
                  // one
                  int requestId =
                      Integer.parseInt(rootSpan.getName().substring("Parent span ".length()));

                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(rootSpan.getName())
                              .hasKind(SpanKind.INTERNAL)
                              .hasNoParent()
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)),
                      span -> assertClientSpan(span, uri, method, 200).hasParent(rootSpan),
                      span ->
                          assertServerSpan(span)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfying(
                                  attrs ->
                                      assertThat(attrs)
                                          .containsEntry("test.request.id", requestId)));
                });
          }

          testRunner.waitAndAssertTraces(assertions);

          pool.shutdown();
        });
  }

  DynamicTest httpsRequest() {
    if (!options.testRemoteConnection || !options.testHttps) {
      return null;
    }
    if (System.getProperty("java.vm.name", "").contains("IBM J9 VM")) {
      System.out.println("IBM JVM has different protocol support for TLS");
      return null;
    }
    return test(
        "https request",
        () -> {
          String method = "GET";
          URI uri = URI.create("https://localhost:" + server.httpsPort() + "/success");

          int responseCode = doRequest(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest successfulGetRequest(String path) {
    return test(
        "successful get request [" + path + "]",
        () -> {
          URI uri = server.resolveAddress(path);
          String method = "GET";
          int responseCode = doRequest(method, uri);

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> assertClientSpan(span, uri, method, responseCode).hasNoParent(),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
        });
  }

  DynamicTest successfulRequestWithParent(String method) {
    return test(
        "successful request with parent [" + method + "]",
        () -> {
          URI uri = server.resolveAddress("/success");
          int responseCode = testRunner.runWithSpan("parent", () -> doRequest(method, uri));

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                      span ->
                          assertClientSpan(span, uri, method, responseCode)
                              .hasParent(trace.getSpan(0)),
                      span -> assertServerSpan(span).hasParent(trace.getSpan(1))));
        });
  }

  DynamicTest shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan(String method) {
    if (!options.testWithClientParent) {
      return null;
    }
    return test(
        "should suppress nested client span if already present under parent client span ["
            + method
            + "]",
        () -> {
          URI uri = server.resolveAddress("/success");
          int responseCode =
              testRunner.runWithHttpClientSpan("parent-client-span", () -> doRequest(method, uri));

          assertThat(responseCode).isEqualTo(200);

          testRunner.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("parent-client-span")
                              .hasKind(SpanKind.CLIENT)
                              .hasNoParent()),
              trace -> trace.hasSpansSatisfyingExactly(HttpClientTests::assertServerSpan));
        });
  }

  // Work-around for lack of @BeforeEach for DynamicTest instances.
  public DynamicTest test(String name, Executable executable) {
    return dynamicTest(
        name,
        () -> {
          testRunner.clearAllExportedData();
          executable.execute();
        });
  }

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
}
