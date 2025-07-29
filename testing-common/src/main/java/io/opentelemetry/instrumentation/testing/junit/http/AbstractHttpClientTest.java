/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.comparingRootSpanAttribute;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttpClientTest<REQUEST> implements HttpClientTypeAdapter<REQUEST> {
  public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration READ_TIMEOUT = Duration.ofSeconds(2);
  public static final String TEST_REQUEST_HEADER = "X-Test-Request";
  public static final String TEST_RESPONSE_HEADER = "X-Test-Response";

  static final String BASIC_AUTH_KEY = "custom-authorization-header";
  static final String BASIC_AUTH_VAL = "plain text auth token";

  /** Returns the connection timeout that should be used when setting up tested clients. */
  protected final Duration connectTimeout() {
    return CONNECTION_TIMEOUT;
  }

  protected final Duration readTimeout() {
    return READ_TIMEOUT;
  }

  protected InstrumentationTestRunner testing;
  private HttpClientTestServer server;

  private HttpClientTestOptions options;

  @BeforeAll
  void setupOptions() {
    HttpClientTestOptions.Builder builder = HttpClientTestOptions.builder();
    configure(builder);
    options = builder.build();
  }

  /**
   * Override this method to configure the {@link HttpClientTestOptions} for the tested HTTP client.
   */
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {}

  // called by the HttpClientInstrumentationExtension
  final void setTesting(InstrumentationTestRunner testing, HttpClientTestServer server) {
    this.testing = testing;
    this.server = server;
  }

  @BeforeEach
  void verifyExtension() {
    if (testing == null) {
      throw new AssertionError(
          "Subclasses of AbstractHttpClientTest must register HttpClientInstrumentationExtension");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/success", "/success?with=params"})
  void successfulGetRequest(String path) throws Exception {
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress(path);
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri, method, responseCode, null)
                        .hasNoParent()
                        .hasStatus(StatusData.unset()),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
  }

  @Test
  void requestWithNonStandardHttpMethod() throws Exception {
    assumeTrue(options.getTestNonStandardHttpMethod());
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress("/success");
    String method = "TEST";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode)
        .isEqualTo("2".equals(options.getHttpProtocolVersion().apply(uri)) ? 400 : 405);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri, HttpConstants._OTHER, responseCode, null)
                        .hasNoParent()
                        .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "POST"})
  void successfulRequestWithParent(String method) throws Exception {
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress("/success");
    int responseCode = testing.runWithSpan("parent", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  assertClientSpan(span, uri, method, responseCode, null)
                      .hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @Test
  void successfulRequestWithNotSampledParent() throws Exception {
    assumeTrue(options.getHasSendRequest());

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
    assumeTrue(options.getTestWithClientParent());
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress("/success");
    int responseCode =
        testing.runWithHttpClientSpan("parent-client-span", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent-client-span", "test-http-server"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-client-span").hasKind(SpanKind.CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span)));
  }

  // FIXME: add tests for POST with large/chunked data

  @Test
  void requestWithCallbackAndParent() throws Throwable {
    assumeTrue(options.getTestCallback());
    assumeTrue(options.getTestCallbackWithParent());

    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result =
        testing.runWithSpan(
            "parent",
            () -> doRequestWithCallback(method, uri, () -> testing.runWithSpan("child", () -> {})));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> assertClientSpan(span, uri, method, 200, null).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)),
              span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void requestWithCallbackAndNoParent() throws Throwable {
    assumeTrue(options.getTestCallback());

    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result =
        doRequestWithCallback(method, uri, () -> testing.runWithSpan("callback", () -> {}));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, 200, null).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void basicRequestWith1Redirect() throws Exception {
    assumeTrue(options.getTestRedirects());

    String method = "GET";
    URI uri = resolveAddress("/redirect");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    if (options.isLowLevelInstrumentation()) {
      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(HttpAttributes.HTTP_REQUEST_RESEND_COUNT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(
                            span, uri, method, options.getResponseCodeOnRedirectError(), null)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          },
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri.resolve("/success"), method, responseCode, 1)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    } else {
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    }
  }

  @Test
  void basicRequestWith2Redirects() throws Exception {
    assumeTrue(options.getTestRedirects());

    String method = "GET";
    URI uri = resolveAddress("/another-redirect");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    if (options.isLowLevelInstrumentation()) {
      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(HttpAttributes.HTTP_REQUEST_RESEND_COUNT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(
                            span, uri, method, options.getResponseCodeOnRedirectError(), null)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          },
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(
                            span,
                            uri.resolve("/redirect"),
                            method,
                            options.getResponseCodeOnRedirectError(),
                            1)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          },
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri.resolve("/success"), method, responseCode, 2)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    } else {
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    }
  }

  @Test
  void circularRedirects() {
    assumeTrue(options.getTestRedirects());
    assumeTrue(options.getTestCircularRedirects());

    String method = "GET";
    URI uri = resolveAddress("/circular-redirect");

    Throwable thrown = catchThrowable(() -> doRequest(method, uri));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.getClientSpanErrorMapper().apply(uri, ex);

    if (options.isLowLevelInstrumentation()) {
      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(HttpAttributes.HTTP_REQUEST_RESEND_COUNT),
          IntStream.range(0, options.getMaxRedirects())
              .mapToObj(i -> makeCircularRedirectAssertForLolLevelTrace(uri, method, i))
              .collect(Collectors.toList()));
    } else {
      testing.waitAndAssertTraces(
          trace -> {
            List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
            assertions.add(
                span ->
                    assertClientSpan(
                            span, uri, method, options.getResponseCodeOnRedirectError(), null)
                        .hasNoParent()
                        .hasException(clientError));
            for (int i = 0; i < options.getMaxRedirects(); i++) {
              assertions.add(span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
            }
            trace.hasSpansSatisfyingExactly(assertions);
          });
    }
  }

  private Consumer<TraceAssert> makeCircularRedirectAssertForLolLevelTrace(
      URI uri, String method, int resendNo) {
    Integer resendCountValue = resendNo > 0 ? resendNo : null;
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                assertClientSpan(
                    span, uri, method, options.getResponseCodeOnRedirectError(), resendCountValue),
            span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
  }

  @Test
  void redirectToSecuredCopiesAuthHeader() throws Exception {
    assumeTrue(options.getTestRedirects());

    String method = "GET";
    URI uri = resolveAddress("/to-secured");

    int responseCode =
        doRequest(method, uri, Collections.singletonMap(BASIC_AUTH_KEY, BASIC_AUTH_VAL));

    assertThat(responseCode).isEqualTo(200);

    if (options.isLowLevelInstrumentation()) {
      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(HttpAttributes.HTTP_REQUEST_RESEND_COUNT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(
                            span, uri, method, options.getResponseCodeOnRedirectError(), null)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          },
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri.resolve("/secured"), method, responseCode, 1)
                        .hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    } else {
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, 200, null).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          });
    }
  }

  // TODO: add basic auth scenario

  @ParameterizedTest
  @CsvSource({"/error,500", "/client-error,400"})
  void errorSpan(String path, int responseCode) {
    assumeTrue(options.getHasSendRequest());

    String method = "GET";
    URI uri = resolveAddress(path);

    testing.runWithSpan(
        "parent",
        () -> {
          try {
            doRequest(method, uri);
          } catch (Throwable ignored) {
            // ignored
          }
        });

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  assertClientSpan(span, uri, method, responseCode, null)
                      .hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @Test
  void reuseRequest() throws Exception {
    assumeTrue(options.getTestReusedRequest());
    assumeTrue(options.getHasSendRequest());

    String method = "GET";
    URI uri = resolveAddress("/success");

    int responseCode = doReusedRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
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
    assumeTrue(options.getHasSendRequest());

    String method = "GET";
    URI uri = resolveAddress("/success");

    int responseCode = doRequestWithExistingTracingHeaders(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void captureHttpHeaders() throws Exception {
    assumeTrue(options.getTestCaptureHttpHeaders());
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress("/success");
    String method = "GET";
    int responseCode =
        doRequest(method, uri, Collections.singletonMap(TEST_REQUEST_HEADER, "test"));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  assertClientSpan(span, uri, method, responseCode, null)
                      .hasNoParent()
                      .hasAttributesSatisfying(
                          asList(
                              equalTo(
                                  AttributeKey.stringArrayKey("http.request.header.x-test-request"),
                                  singletonList("test")),
                              equalTo(
                                  AttributeKey.stringArrayKey(
                                      "http.response.header.x-test-response"),
                                  singletonList("test")))),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void connectionErrorUnopenedPort() {
    assumeTrue(options.getTestConnectionFailure());
    assumeTrue(options.getHasSendRequest());

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
    Throwable clientError = options.getClientSpanErrorMapper().apply(uri, ex);

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
                  assertClientSpan(span, uri, method, null, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError));
        });
  }

  @Test
  void connectionErrorUnopenedPortWithCallback() throws Exception {
    assumeTrue(options.getTestConnectionFailure());
    assumeTrue(options.getTestCallback());
    assumeTrue(options.getTestErrorWithCallback());

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
    Throwable clientError = options.getClientSpanErrorMapper().apply(uri, ex);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  assertClientSpan(span, uri, method, null, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError),
              span ->
                  span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void connectionErrorNonRoutableAddress() {
    assumeTrue(options.getTestRemoteConnection());
    assumeTrue(options.getHasSendRequest());

    String method = "HEAD";
    URI uri = URI.create(options.getTestHttps() ? "https://192.0.2.1/" : "http://192.0.2.1/");

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> doRequest(method, uri)));
    Throwable ex;
    if (thrown instanceof ExecutionException) {
      ex = thrown.getCause();
    } else {
      ex = thrown;
    }
    Throwable clientError = options.getClientSpanErrorMapper().apply(uri, ex);

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
                  assertClientSpan(span, uri, method, null, null)
                      .hasParent(trace.getSpan(0))
                      .hasException(clientError));
        });
  }

  @Test
  void readTimedOut() {
    assumeTrue(options.getTestReadTimeout());
    assumeTrue(options.getHasSendRequest());

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
    Throwable clientError = options.getClientSpanErrorMapper().apply(uri, ex);

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
                  assertClientSpan(span, uri, method, null, null)
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
    assumeTrue(options.getTestRemoteConnection());
    assumeTrue(options.getTestHttps());
    assumeTrue(options.getHasSendRequest());

    String method = "GET";
    URI uri = URI.create("https://localhost:" + server.httpsPort() + "/success");

    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void httpClientMetrics() throws Exception {
    assumeTrue(options.getHasSendRequest());

    URI uri = resolveAddress("/success");
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "http.client.request.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("Duration of HTTP client requests.")
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point -> point.hasSumGreaterThan(0.0)))));
  }

  /**
   * This test fires a large number of concurrent requests. Each request first hits a HTTP server
   * and then makes another client request. The goal of this test is to verify that in highly
   * concurrent environment our instrumentations for http clients (especially inherently concurrent
   * ones, such as Netty or Reactor) correctly propagate trace context.
   */
  @Test
  void highConcurrency() {
    assumeTrue(options.getHasSendRequest());

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
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)),
                span -> assertClientSpan(span, uri, method, 200, null).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)));
          });
    }

    testing.waitAndAssertTraces(assertions);

    pool.shutdown();
  }

  @Test
  void highConcurrencyWithCallback() {
    assumeTrue(options.getTestCallback());
    assumeTrue(options.getTestCallbackWithParent());

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
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)),
                span -> assertClientSpan(span, uri, method, 200, null).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)),
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
        options.getSingleConnectionFactory().apply("localhost", server.httpPort());
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)),
                span -> assertClientSpan(span, uri, method, 200, null).hasParent(rootSpan),
                span ->
                    assertServerSpan(span)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.longKey("test.request.id"), requestId)));
          });
    }

    testing.waitAndAssertTraces(assertions);

    pool.shutdown();
  }

  @Test
  void spanEndsAfterBodyReceived() throws Exception {
    assumeTrue(options.isSpanEndsAfterBody());
    assumeTrue(options.getHasSendRequest());

    String method = "GET";
    URI uri = resolveAddress("/long-request");

    int responseCode =
        doRequest(
            method,
            uri,
            // the time that server waits before completing the response
            Collections.singletonMap("delay", String.valueOf(TimeUnit.SECONDS.toMillis(1))));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  assertClientSpan(span, uri, method, 200, null)
                      .hasNoParent()
                      .hasStatus(StatusData.unset()),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          SpanData span = trace.getSpan(0);
          // make sure the span is at least as long as the delay we set when sending the request
          assertThat(
                  span.getEndEpochNanos() - span.getStartEpochNanos()
                      >= TimeUnit.SECONDS.toNanos(1))
              .describedAs("Span duration should be at least 1s")
              .isTrue();
        });
  }

  @Test
  void spanEndsAfterHeadersReceived() throws Exception {
    assumeTrue(options.isSpanEndsAfterHeaders());

    String method = "GET";
    URI uri = resolveAddress("/long-request");

    int responseCode =
        doRequest(
            method,
            uri,
            // the time that server waits before completing the response, we expect the response
            // headers to arrive much sooner
            Collections.singletonMap("delay", String.valueOf(TimeUnit.SECONDS.toMillis(2))));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  assertClientSpan(span, uri, method, 200, null)
                      .hasNoParent()
                      .hasStatus(StatusData.unset()),
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          SpanData span = trace.getSpan(0);
          // verify that the span length is less than the delay used to complete the response body
          assertThat(
                  span.getEndEpochNanos() - span.getStartEpochNanos()
                      <= TimeUnit.SECONDS.toNanos(2))
              .describedAs("Span duration should be less than 2s")
              .isTrue();
        });
  }

  // Visible for spock bridge.
  SpanDataAssert assertClientSpan(
      SpanDataAssert span,
      URI uri,
      String method,
      @Nullable Integer responseCode,
      @Nullable Integer resendCount) {
    Set<AttributeKey<?>> httpClientAttributes = options.getHttpAttributes().apply(uri);
    return span.hasName(options.getExpectedClientSpanNameMapper().apply(uri, method))
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            attrs -> {
              // we're opting out of these attributes in the new semconv
              assertThat(attrs)
                  .doesNotContainKey(NetworkAttributes.NETWORK_TRANSPORT)
                  .doesNotContainKey(NetworkAttributes.NETWORK_TYPE)
                  .doesNotContainKey(NetworkAttributes.NETWORK_PROTOCOL_NAME);
              if (httpClientAttributes.contains(NetworkAttributes.NETWORK_PROTOCOL_VERSION)) {
                assertThat(attrs)
                    .containsEntry(
                        NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                        options.getHttpProtocolVersion().apply(uri));
              }

              if (httpClientAttributes.contains(ServerAttributes.SERVER_ADDRESS)) {
                assertThat(attrs).containsEntry(ServerAttributes.SERVER_ADDRESS, uri.getHost());
              }
              if (httpClientAttributes.contains(ServerAttributes.SERVER_PORT)) {
                int uriPort = uri.getPort();
                if (uriPort <= 0) {
                  if (attrs.get(ServerAttributes.SERVER_PORT) != null) {
                    int effectivePort = "https".equals(uri.getScheme()) ? 443 : 80;
                    assertThat(attrs).containsEntry(ServerAttributes.SERVER_PORT, effectivePort);
                  }
                  // alternatively, peer port is not emitted -- and that's fine too
                } else {
                  assertThat(attrs).containsEntry(ServerAttributes.SERVER_PORT, uriPort);
                }
              }

              if (uri.getPort() != PortUtils.UNUSABLE_PORT && !uri.getHost().equals("192.0.2.1")) {
                // TODO: Move to test knob rather than always treating as optional
                if (attrs.get(NetworkAttributes.NETWORK_PEER_ADDRESS) != null) {
                  assertThat(attrs)
                      .hasEntrySatisfying(
                          NetworkAttributes.NETWORK_PEER_ADDRESS,
                          addr -> assertThat(addr).isIn("127.0.0.1", "0:0:0:0:0:0:0:1"));
                }
                if (attrs.get(NetworkAttributes.NETWORK_PEER_PORT) != null) {
                  assertThat(attrs)
                      .containsEntry(
                          NetworkAttributes.NETWORK_PEER_PORT,
                          Objects.equals(uri.getScheme(), "https")
                              ? server.httpsPort()
                              : server.httpPort());
                }
              }

              if (httpClientAttributes.contains(UrlAttributes.URL_FULL)) {
                assertThat(attrs).containsEntry(UrlAttributes.URL_FULL, uri.toString());
              }
              if (httpClientAttributes.contains(HttpAttributes.HTTP_REQUEST_METHOD)) {
                assertThat(attrs).containsEntry(HttpAttributes.HTTP_REQUEST_METHOD, method);
              }

              // opt-in, not collected by default
              assertThat(attrs).doesNotContainKey(UserAgentAttributes.USER_AGENT_ORIGINAL);

              if (responseCode != null) {
                assertThat(attrs)
                    .containsEntry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) responseCode);
                if (responseCode >= 400) {
                  assertThat(attrs)
                      .containsEntry(ErrorAttributes.ERROR_TYPE, String.valueOf(responseCode));
                }
              } else {
                assertThat(attrs).doesNotContainKey(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
                // TODO: add more detailed assertions, per url
                assertThat(attrs).containsKey(ErrorAttributes.ERROR_TYPE);
              }

              if (resendCount != null) {
                assertThat(attrs)
                    .containsEntry(HttpAttributes.HTTP_REQUEST_RESEND_COUNT, (long) resendCount);
              } else {
                assertThat(attrs).doesNotContainKey(HttpAttributes.HTTP_REQUEST_RESEND_COUNT);
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
    REQUEST request = buildRequest(method, uri, headers);
    return sendRequest(request, method, uri, headers);
  }

  private int doReusedRequest(String method, URI uri) throws Exception {
    REQUEST request = buildRequest(method, uri, Collections.emptyMap());
    sendRequest(request, method, uri, Collections.emptyMap());
    return sendRequest(request, method, uri, Collections.emptyMap());
  }

  private int doRequestWithExistingTracingHeaders(String method, URI uri) throws Exception {
    Map<String, String> headers = new HashMap<>();
    for (String field :
        testing.getOpenTelemetry().getPropagators().getTextMapPropagator().fields()) {
      headers.put(field, "12345789");
    }
    REQUEST request = buildRequest(method, uri, headers);
    return sendRequest(request, method, uri, headers);
  }

  private HttpClientResult doRequestWithCallback(String method, URI uri, Runnable callback)
      throws Exception {
    return doRequestWithCallback(method, uri, Collections.emptyMap(), callback);
  }

  private HttpClientResult doRequestWithCallback(
      String method, URI uri, Map<String, String> headers, Runnable callback) throws Exception {
    REQUEST request = buildRequest(method, uri, headers);
    HttpClientResult httpClientResult = new HttpClientResult(callback);
    sendRequestWithCallback(request, method, uri, headers, httpClientResult);
    return httpClientResult;
  }

  protected final URI resolveAddress(String path) {
    return URI.create("http://localhost:" + server.httpPort() + path);
  }
}
