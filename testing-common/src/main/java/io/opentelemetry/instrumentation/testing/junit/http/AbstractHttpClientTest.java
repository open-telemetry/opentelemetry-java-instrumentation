/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.comparingRootSpanAttribute;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.Assume.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    URI uri = resolveAddress(path);
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, responseCode, null).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
  }

  @Test
  void requestWithNonStandardHttpMethod() throws Exception {
    assumeTrue(SemconvStability.emitStableHttpSemconv() && options.getTestNonStandardHttpMethod());

    URI uri = resolveAddress("/success");
    String method = "TEST";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(405);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri, HttpConstants._OTHER, responseCode, null)
                        .hasNoParent()
                        .hasAttribute(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method)));
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
              span ->
                  assertClientSpan(span, uri, method, responseCode, null)
                      .hasParent(trace.getSpan(0)),
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
    assumeTrue(options.getTestWithClientParent());

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
    assumeFalse(options.getTestCallbackWithImplicitParent());

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
  void requestWithCallbackAndImplicitParent() throws Throwable {
    assumeTrue(options.getTestCallbackWithImplicitParent());

    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result =
        doRequestWithCallback(method, uri, () -> testing.runWithSpan("callback", () -> {}));

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, uri, method, 200, null).hasNoParent(),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
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
              span -> assertClientSpan(span, uri, method, 500, null).hasParent(trace.getSpan(0)),
              span -> assertServerSpan(span).hasParent(trace.getSpan(1)));
        });
  }

  @Test
  void reuseRequest() throws Exception {
    assumeTrue(options.getTestReusedRequest());

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
    URI uri = resolveAddress("/success");
    String method = "GET";
    int responseCode =
        doRequest(method, uri, Collections.singletonMap(TEST_REQUEST_HEADER, "test"));

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                assertClientSpan(span, uri, method, responseCode, null).hasNoParent();
                span.hasAttributesSatisfying(
                    equalTo(
                        AttributeKey.stringArrayKey("http.request.header.x_test_request"),
                        singletonList("test")),
                    equalTo(
                        AttributeKey.stringArrayKey("http.response.header.x_test_response"),
                        singletonList("test")));
              },
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }

  @Test
  void connectionErrorUnopenedPort() {
    assumeTrue(options.getTestConnectionFailure());

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

    String durationInstrumentName =
        SemconvStability.emitStableHttpSemconv()
            ? "http.client.request.duration"
            : "http.client.duration";
    String durationInstrumentDescription =
        SemconvStability.emitStableHttpSemconv()
            ? "Duration of HTTP client requests."
            : "The duration of the outbound HTTP request";

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        durationInstrumentName,
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription(durationInstrumentDescription)
                        .hasUnit(SemconvStability.emitStableHttpSemconv() ? "s" : "ms")
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

  // Visible for spock bridge.
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  SpanDataAssert assertClientSpan(
      SpanDataAssert span,
      URI uri,
      String method,
      @Nullable Integer responseCode,
      @Nullable Integer resendCount) {
    Set<AttributeKey<?>> httpClientAttributes =
        getAttributeKeys(options.getHttpAttributes().apply(uri));
    return span.hasName(options.getExpectedClientSpanNameMapper().apply(uri, method))
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            attrs -> {
              // TODO: Move to test knob rather than always treating as optional
              if (SemconvStability.emitOldHttpSemconv()
                  && attrs.get(SemanticAttributes.NET_TRANSPORT) != null) {
                assertThat(attrs).containsEntry(SemanticAttributes.NET_TRANSPORT, IP_TCP);
              }
              if (SemconvStability.emitStableHttpSemconv()) {
                // we're opting out of these attributes in the new semconv
                assertThat(attrs)
                    .doesNotContainKey(SemanticAttributes.NETWORK_TRANSPORT)
                    .doesNotContainKey(SemanticAttributes.NETWORK_TYPE);
              }
              AttributeKey<String> netProtocolKey =
                  getAttributeKey(SemanticAttributes.NET_PROTOCOL_NAME);
              if (httpClientAttributes.contains(netProtocolKey)) {
                assertThat(attrs).containsEntry(netProtocolKey, "http");
              }
              AttributeKey<String> netProtocolVersionKey =
                  getAttributeKey(SemanticAttributes.NET_PROTOCOL_VERSION);
              if (httpClientAttributes.contains(netProtocolVersionKey)) {
                // TODO(anuraaga): Support HTTP/2
                assertThat(attrs).containsEntry(netProtocolVersionKey, "1.1");
              }
              AttributeKey<String> netPeerNameKey =
                  getAttributeKey(SemanticAttributes.NET_PEER_NAME);
              if (httpClientAttributes.contains(netPeerNameKey)) {
                assertThat(attrs).containsEntry(netPeerNameKey, uri.getHost());
              }
              AttributeKey<Long> netPeerPortKey = getAttributeKey(SemanticAttributes.NET_PEER_PORT);
              if (httpClientAttributes.contains(netPeerPortKey)) {
                int uriPort = uri.getPort();
                // default values are ignored
                if (uriPort <= 0 || uriPort == 80 || uriPort == 443) {
                  assertThat(attrs).doesNotContainKey(netPeerPortKey);
                } else {
                  assertThat(attrs).containsEntry(netPeerPortKey, uriPort);
                }
              }

              if (uri.getPort() == PortUtils.UNUSABLE_PORT || uri.getHost().equals("192.0.2.1")) {
                // In these cases the peer connection is not established, so the HTTP client should
                // not report any socket-level attributes
                assertThat(attrs).doesNotContainKey("net.sock.family");
                // TODO netty sometimes reports net.sock.peer.* in connection error test
                // .doesNotContainKey("net.sock.peer.addr")
                // .doesNotContainKey("net.sock.peer.name")
                // .doesNotContainKey("net.sock.peer.port");

              } else {
                // TODO: Move to test knob rather than always treating as optional
                AttributeKey<String> netSockPeerAddrKey =
                    getAttributeKey(SemanticAttributes.NET_SOCK_PEER_ADDR);
                if (attrs.get(netSockPeerAddrKey) != null) {
                  assertThat(attrs).containsEntry(netSockPeerAddrKey, "127.0.0.1");
                }
                AttributeKey<Long> netSockPeerPortKey =
                    getAttributeKey(SemanticAttributes.NET_SOCK_PEER_PORT);
                if (attrs.get(netSockPeerPortKey) != null) {
                  assertThat(attrs)
                      .containsEntry(
                          netSockPeerPortKey,
                          Objects.equals(uri.getScheme(), "https")
                              ? server.httpsPort()
                              : server.httpPort());
                }
              }

              AttributeKey<String> httpUrlKey = getAttributeKey(SemanticAttributes.HTTP_URL);
              if (httpClientAttributes.contains(httpUrlKey)) {
                assertThat(attrs).containsEntry(httpUrlKey, uri.toString());
              }
              AttributeKey<String> httpMethodKey = getAttributeKey(SemanticAttributes.HTTP_METHOD);
              if (httpClientAttributes.contains(httpMethodKey)) {
                assertThat(attrs).containsEntry(httpMethodKey, method);
              }
              if (httpClientAttributes.contains(SemanticAttributes.USER_AGENT_ORIGINAL)) {
                String userAgent = options.getUserAgent();
                if (userAgent != null
                    || attrs.get(SemanticAttributes.USER_AGENT_ORIGINAL) != null) {
                  assertThat(attrs)
                      .hasEntrySatisfying(
                          SemanticAttributes.USER_AGENT_ORIGINAL,
                          actual -> {
                            if (userAgent != null) {
                              assertThat(actual).startsWith(userAgent);
                            } else {
                              assertThat(actual).isNull();
                            }
                          });
                }
              }
              AttributeKey<Long> httpRequestLengthKey =
                  getAttributeKey(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH);
              if (attrs.get(httpRequestLengthKey) != null) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        httpRequestLengthKey, length -> assertThat(length).isNotNegative());
              }
              AttributeKey<Long> httpResponseLengthKey =
                  getAttributeKey(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH);
              if (attrs.get(httpResponseLengthKey) != null) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        httpResponseLengthKey, length -> assertThat(length).isNotNegative());
              }

              AttributeKey<Long> httpResponseStatusKey =
                  getAttributeKey(SemanticAttributes.HTTP_STATUS_CODE);
              if (responseCode != null) {
                assertThat(attrs).containsEntry(httpResponseStatusKey, (long) responseCode);
                if (responseCode >= 400 && SemconvStability.emitStableHttpSemconv()) {
                  assertThat(attrs)
                      .containsEntry(HttpAttributes.ERROR_TYPE, String.valueOf(responseCode));
                }
              } else {
                assertThat(attrs).doesNotContainKey(httpResponseStatusKey);
                if (SemconvStability.emitStableHttpSemconv()) {
                  // TODO: add more detailed assertions, per url
                  assertThat(attrs).containsKey(stringKey("error.type"));
                }
              }

              if (resendCount != null) {
                assertThat(attrs)
                    .containsEntry(HttpAttributes.HTTP_REQUEST_RESEND_COUNT, (long) resendCount);
              } else {
                assertThat(attrs).doesNotContainKey(HttpAttributes.HTTP_REQUEST_RESEND_COUNT);
              }
            });
  }

  protected static <T> AttributeKey<T> getAttributeKey(AttributeKey<T> oldKey) {
    return SemconvStabilityUtil.getAttributeKey(oldKey);
  }

  private static Set<AttributeKey<?>> getAttributeKeys(Set<AttributeKey<?>> oldKeys) {
    if (!SemconvStability.emitStableHttpSemconv()) {
      return oldKeys;
    }
    Set<AttributeKey<?>> result = new HashSet<>();
    for (AttributeKey<?> key : oldKeys) {
      result.add(getAttributeKey(key));
    }
    return result;
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
