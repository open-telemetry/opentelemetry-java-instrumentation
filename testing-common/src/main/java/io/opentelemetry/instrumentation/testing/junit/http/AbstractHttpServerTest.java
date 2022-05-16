/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.HttpRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpRequestBuilder;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttpServerTest<SERVER> {
  private static final Logger logger = LoggerFactory.getLogger(AbstractHttpServerTest.class);

  public static final String TEST_REQUEST_HEADER = "X-Test-Request";
  public static final String TEST_RESPONSE_HEADER = "X-Test-Response";

  public static final String TEST_CLIENT_IP = "1.1.1.1";
  public static final String TEST_USER_AGENT = "test-user-agent";

  private final HttpServerTestOptions options = new HttpServerTestOptions();
  private InstrumentationTestRunner testing;
  private SERVER server;
  public WebClient client;
  public int port;
  public URI address;

  protected abstract SERVER setupServer();

  protected abstract void stopServer(SERVER server);

  protected final InstrumentationTestRunner testing() {
    return testing;
  }

  @BeforeAll
  void setupOptions() {
    options.expectedServerSpanNameMapper = this::expectedServerSpanName;
    options.expectedHttpRoute = this::expectedHttpRoute;

    configure(options);

    if (address == null) {
      address = buildAddress();
    }

    server = setupServer();
    if (server != null) {
      logger.info(
          getClass().getName()
              + " http server started at: http://localhost:"
              + port
              + options.contextPath);
    }
  }

  @AfterAll
  void cleanup() {
    if (server == null) {
      logger.info(getClass().getName() + " can't stop null server");
      return;
    }
    stopServer(server);
    server = null;
    logger.info(getClass().getName() + " http server stopped at: http://localhost:" + port + "/");
  }

  protected URI buildAddress() {
    try {
      return new URI("http://localhost:" + port + options.contextPath + "/");
    } catch (URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
  }

  protected void configure(HttpServerTestOptions options) {}

  @BeforeEach
  void verifyExtension() {
    if (testing == null) {
      throw new AssertionError(
          "Subclasses of AbstractHttpServerTest must register HttpServerInstrumentationExtension");
    }
  }

  public static <T> T controller(ServerEndpoint endpoint, Supplier<T> closure) {
    assert Span.current().getSpanContext().isValid() : "Controller should have a parent span.";
    if (endpoint == NOT_FOUND) {
      return closure.get();
    }
    return GlobalTraceUtil.runWithSpan("controller", () -> closure.get());
  }

  String resolveAddress(ServerEndpoint uri) {
    String url = uri.resolvePath(address).toString();
    // Force HTTP/1 via h1c so upgrade requests don't show up as traces
    url = url.replace("http://", "h1c://");
    if (uri.getQuery() != null) {
      url += "?" + uri.getQuery();
    }
    return url;
  }

  private AggregatedHttpRequest request(ServerEndpoint uri, String method) {
    return AggregatedHttpRequest.of(HttpMethod.valueOf(method), resolveAddress(uri));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 4, 50})
  void successfulGetRequest(int count) {
    String method = "GET";
    AggregatedHttpRequest request = request(SUCCESS, method);
    List<AggregatedHttpResponse> responses = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      responses.add(client.execute(request).aggregate().join());
    }

    for (AggregatedHttpResponse response : responses) {
      assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
      assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());
    }

    assertTheTraces(count, null, null, method, SUCCESS, responses.get(0));
  }

  @Test
  void successfulGetRequestWithParent() {
    String method = "GET";
    String traceId = "00000000000000000000000000000123";
    String parentId = "0000000000000456";
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            // intentionally sending mixed-case "tracePARENT" to make sure that TextMapGetters are
            // not case-sensitive
            request(SUCCESS, method).headers().toBuilder()
                .set("tracePARENT", "00-" + traceId + "-" + parentId + "-01")
                .build());
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    assertTheTraces(1, traceId, parentId, "GET", SUCCESS, response);
  }

  @Test
  void tracingHeaderIsCaseInsensitive() {
    String method = "GET";
    String traceId = "00000000000000000000000000000123";
    String parentId = "0000000000000456";
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            request(SUCCESS, method).headers().toBuilder()
                .set("TRACEPARENT", "00-" + traceId + "-" + parentId + "-01")
                .build());
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    assertTheTraces(1, traceId, parentId, "GET", SUCCESS, response);
  }

  @ParameterizedTest
  @EnumSource(
      value = ServerEndpoint.class,
      names = {"SUCCESS", "QUERY_PARAM"})
  void requestWithQueryString(ServerEndpoint endpoint) {
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(endpoint.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(endpoint.getBody());

    assertTheTraces(1, null, null, method, endpoint, response);
  }

  @Test
  void requestWithRedirect() {
    assumeTrue(options.testRedirect);

    String method = "GET";
    AggregatedHttpRequest request = request(REDIRECT, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(REDIRECT.getStatus());
    assertThat(response.headers().get("location"))
        .satisfiesAnyOf(
            location -> assertThat(location).isEqualTo(REDIRECT.getBody()),
            location ->
                assertThat(new URI(location).normalize().toString())
                    .isEqualTo(address.resolve(REDIRECT.getBody()).toString()));

    assertTheTraces(1, null, null, method, REDIRECT, response);
  }

  @Test
  void requestWithError() {
    assumeTrue(options.testError);

    String method = "GET";
    AggregatedHttpRequest request = request(ERROR, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(ERROR.getStatus());
    if (options.testErrorBody) {
      assertThat(response.contentUtf8()).isEqualTo(ERROR.getBody());
    }

    assertTheTraces(1, null, null, method, ERROR, response);
  }

  @Test
  void requestWithException() {
    assumeTrue(options.testException);

    // async servlet tests may produce uncaught exceptions
    // awaitility rethrows uncaught exceptions while it is waiting on a condition
    Awaitility.doNotCatchUncaughtExceptionsByDefault();
    try {
      String method = "GET";
      AggregatedHttpRequest request = request(EXCEPTION, method);
      AggregatedHttpResponse response = client.execute(request).aggregate().join();

      assertThat(response.status().code()).isEqualTo(EXCEPTION.getStatus());

      assertTheTraces(1, null, null, method, EXCEPTION, response);
    } finally {
      Awaitility.reset();
    }
  }

  @Test
  void requestForNotFound() {
    assumeTrue(options.testNotFound);

    String method = "GET";
    AggregatedHttpRequest request = request(NOT_FOUND, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(NOT_FOUND.getStatus());

    assertTheTraces(1, null, null, method, NOT_FOUND, response);
  }

  @Test
  void requestWithPathParameter() {
    assumeTrue(options.testPathParam);

    String method = "GET";
    AggregatedHttpRequest request = request(PATH_PARAM, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(PATH_PARAM.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(PATH_PARAM.getBody());

    assertTheTraces(1, null, null, method, PATH_PARAM, response);
  }

  @Test
  void captureHttpHeaders() {
    assumeTrue(options.testCaptureHttpHeaders);

    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            request(CAPTURE_HEADERS, "GET").headers().toBuilder()
                .add(TEST_REQUEST_HEADER, "test")
                .build());
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(CAPTURE_HEADERS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(CAPTURE_HEADERS.getBody());

    assertTheTraces(1, null, null, "GET", CAPTURE_HEADERS, response);
  }

  @Test
  void captureRequestParameters() {
    assumeTrue(options.testCaptureRequestParameters);

    QueryParams formBody = QueryParams.builder().add("test-parameter", "test value õäöü").build();
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(HttpMethod.POST, resolveAddress(CAPTURE_PARAMETERS))
                .contentType(MediaType.FORM_DATA)
                .build(),
            HttpData.ofUtf8(formBody.toQueryString()));
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(CAPTURE_PARAMETERS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(CAPTURE_PARAMETERS.getBody());

    assertTheTraces(1, null, null, "POST", CAPTURE_PARAMETERS, response);
  }

  /**
   * This test fires a bunch of parallel request to the fixed backend endpoint. That endpoint is
   * supposed to create a new child span in the context of the SERVER span. That child span is
   * expected to have an attribute called "test.request.id". The value of that attribute should be
   * the value of request's parameter called "id".
   *
   * <p>This test then asserts that there is the correct number of traces (one per request executed)
   * and that each trace has exactly three spans and both first and the last spans have
   * "test.request.id" attribute with equal value. Server span is not going to have that attribute
   * because it is not under the control of this test.
   *
   * <p>This way we verify that child span created by the server actually corresponds to the client
   * request.
   */
  @Test
  void highConcurrency() throws InterruptedException {
    int count = 100;
    ServerEndpoint endpoint = INDEXED_CHILD;
    CountDownLatch latch = new CountDownLatch(count);

    TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    TextMapSetter<HttpRequestBuilder> setter = HttpRequestBuilder::header;

    for (int i = 0; i < count; i++) {
      int index = i;
      HttpRequestBuilder request =
          HttpRequest.builder()
              // Force HTTP/1 via h1c so upgrade requests don't show up as traces
              .get(endpoint.resolvePath(address).toString().replace("http://", "h1c://"))
              .queryParam(ServerEndpoint.ID_PARAMETER_NAME, index);

      testing.runWithSpan(
          "client " + index,
          () -> {
            Span.current().setAttribute(ServerEndpoint.ID_ATTRIBUTE_NAME, index);
            propagator.inject(Context.current(), request, setter);
            client
                .execute(request.build())
                .aggregate()
                .whenComplete((result, throwable) -> latch.countDown());
          });
    }
    latch.await();

    assertHighConcurrency(count);
  }

  protected void assertHighConcurrency(int count) {
    ServerEndpoint endpoint = INDEXED_CHILD;
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      assertions.add(
          trace -> {
            SpanData rootSpan = trace.getSpan(0);
            // Traces can be in arbitrary order, let us find out the request id of the current one
            int requestId = Integer.parseInt(rootSpan.getName().substring("client ".length()));

            List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
            spanAssertions.add(
                span ->
                    span.hasName(rootSpan.getName())
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            attrs ->
                                assertThat(attrs)
                                    .containsEntry(ServerEndpoint.ID_ATTRIBUTE_NAME, requestId)));
            spanAssertions.add(
                span -> assertIndexedServerSpan(span, requestId).hasParent(rootSpan));

            if (options.hasHandlerSpan.test(endpoint)) {
              spanAssertions.add(
                  span -> assertHandlerSpan(span, "GET", endpoint).hasParent(trace.getSpan(1)));
            }

            int parentIndex = spanAssertions.size() - 1;
            spanAssertions.add(
                span ->
                    assertIndexedControllerSpan(span, requestId)
                        .hasParent(trace.getSpan(parentIndex)));

            trace.hasSpansSatisfyingExactly(spanAssertions);
          });
    }
    testing.waitAndAssertTraces(assertions);
  }

  // NOTE: this method does not currently implement asserting all the span types that groovy
  // HttpServerTest does
  protected void assertTheTraces(
      int size,
      String traceId,
      String parentId,
      String method,
      ServerEndpoint endpoint,
      AggregatedHttpResponse response) {
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      assertions.add(
          trace -> {
            List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
            spanAssertions.add(
                span -> {
                  assertServerSpan(span, method, endpoint);
                  if (parentId != null) {
                    span.hasTraceId(traceId).hasParentSpanId(parentId);
                  } else {
                    span.hasNoParent();
                  }
                });

            if (options.hasHandlerSpan.test(endpoint)) {
              spanAssertions.add(
                  span -> {
                    assertHandlerSpan(span, method, endpoint);
                    span.hasParent(trace.getSpan(0));
                  });
            }

            if (endpoint != NOT_FOUND) {
              int parentIndex = spanAssertions.size() - 1;
              spanAssertions.add(
                  span -> {
                    assertControllerSpan(
                        span, endpoint == EXCEPTION ? options.expectedException : null);
                    span.hasParent(trace.getSpan(parentIndex));
                  });
            }

            if (options.hasResponseSpan.test(endpoint)) {
              int parentIndex = spanAssertions.size() - 1;
              spanAssertions.add(
                  span -> {
                    assertResponseSpan(span, method, endpoint);
                    span.hasParent(trace.getSpan(parentIndex));
                  });
            }

            if (options.hasErrorPageSpans.test(endpoint)) {
              spanAssertions.addAll(errorPageSpanAssertions(method, endpoint));
            }

            trace.hasSpansSatisfyingExactly(spanAssertions);
          });
    }

    testing.waitAndAssertTraces(assertions);
  }

  protected SpanDataAssert assertControllerSpan(SpanDataAssert span, Throwable expectedException) {
    span.hasName("controller").hasKind(SpanKind.INTERNAL);
    if (expectedException != null) {
      span.hasStatus(StatusData.error());
      span.hasException(expectedException);
    }
    return span;
  }

  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    throw new UnsupportedOperationException(
        "assertHandlerSpan not implemented in " + getClass().getName());
  }

  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    throw new UnsupportedOperationException(
        "assertResponseSpan not implemented in " + getClass().getName());
  }

  protected List<Consumer<SpanDataAssert>> errorPageSpanAssertions(
      String method, ServerEndpoint endpoint) {
    throw new UnsupportedOperationException(
        "errorPageSpanAssertions not implemented in " + getClass().getName());
  }

  protected SpanDataAssert assertServerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    Set<AttributeKey<?>> httpAttributes = options.httpAttributes.apply(endpoint);

    String expectedRoute = options.expectedHttpRoute.apply(endpoint);
    String name =
        expectedRoute != null
            ? expectedRoute
            : options.expectedServerSpanNameMapper.apply(endpoint, method);

    span.hasName(name).hasKind(SpanKind.SERVER);
    if (endpoint.status >= 500) {
      span.hasStatus(StatusData.error());
    }

    if (endpoint == EXCEPTION && options.hasExceptionOnServerSpan.test(endpoint)) {
      span.hasException(options.expectedException);
    }

    span.hasAttributesSatisfying(
        attrs -> {
          if (httpAttributes.contains(SemanticAttributes.NET_TRANSPORT)) {
            assertThat(attrs)
                .containsEntry(
                    SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP);
          }
          if (httpAttributes.contains(SemanticAttributes.NET_PEER_PORT)) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.NET_PEER_PORT,
                    value ->
                        assertThat(value)
                            .isInstanceOf(Long.class)
                            .isNotEqualTo(Long.valueOf(port)));
          }
          if (httpAttributes.contains(SemanticAttributes.NET_PEER_IP)
              || attrs.get(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH) != null) {
            assertThat(attrs)
                .containsEntry(SemanticAttributes.NET_PEER_IP, options.peerIp.apply(endpoint));
          }

          assertThat(attrs)
              .hasEntrySatisfying(
                  SemanticAttributes.HTTP_CLIENT_IP,
                  entry ->
                      assertThat(entry)
                          .satisfiesAnyOf(
                              value -> assertThat(value).isNull(),
                              value -> assertThat(value).isEqualTo(TEST_CLIENT_IP)));
          assertThat(attrs).containsEntry(SemanticAttributes.HTTP_METHOD, method);
          assertThat(attrs).containsEntry(SemanticAttributes.HTTP_STATUS_CODE, endpoint.status);

          assertThat(attrs)
              .hasEntrySatisfying(
                  SemanticAttributes.HTTP_FLAVOR, entry -> assertThat(entry).isIn("1.1", "2.0"));
          assertThat(attrs).containsEntry(SemanticAttributes.HTTP_USER_AGENT, TEST_USER_AGENT);

          assertThat(attrs).containsEntry(SemanticAttributes.HTTP_SCHEME, "http");
          assertThat(attrs)
              .hasEntrySatisfying(
                  SemanticAttributes.HTTP_HOST,
                  entry -> assertThat(entry).isIn("localhost", "localhost:" + port));
          if (endpoint != INDEXED_CHILD) {
            assertThat(attrs)
                .containsEntry(
                    SemanticAttributes.HTTP_TARGET,
                    endpoint.resolvePath(address).getPath()
                        + (endpoint == QUERY_PARAM ? "?" + endpoint.body : ""));
          }

          if (httpAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)
              || attrs.get(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                    entry -> assertThat(entry).isInstanceOf(Long.class));
          }
          if (httpAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)
              || attrs.get(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                    entry -> assertThat(entry).isInstanceOf(Long.class));
          }
          if (httpAttributes.contains(SemanticAttributes.HTTP_SERVER_NAME)) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.HTTP_SERVER_NAME,
                    entry -> assertThat(entry).isInstanceOf(String.class));
          }
          if (httpAttributes.contains(SemanticAttributes.HTTP_ROUTE)) {
            if (expectedRoute != null) {
              assertThat(attrs).containsEntry(SemanticAttributes.HTTP_ROUTE, expectedRoute);
            }
          }

          if (endpoint == CAPTURE_HEADERS) {
            assertThat(attrs)
                .containsEntry("http.request.header.x_test_request", new String[] {"test"});
            assertThat(attrs)
                .containsEntry("http.response.header.x_test_response", new String[] {"test"});
          }
          if (endpoint == CAPTURE_PARAMETERS) {
            assertThat(attrs)
                .containsEntry(
                    "servlet.request.parameter.test_parameter", new String[] {"test value õäöü"});
          }
        });

    return span;
  }

  protected SpanDataAssert assertIndexedServerSpan(SpanDataAssert span, int requestId) {
    ServerEndpoint endpoint = INDEXED_CHILD;
    String method = "GET";
    assertServerSpan(span, method, endpoint);

    span.hasAttributesSatisfying(
        attrs ->
            assertThat(attrs)
                .containsEntry(
                    SemanticAttributes.HTTP_TARGET,
                    endpoint.resolvePath(address).getPath() + "?id=" + requestId));

    return span;
  }

  protected SpanDataAssert assertIndexedControllerSpan(SpanDataAssert span, int requestId) {
    span.hasName("controller")
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            attrs -> assertThat(attrs).containsEntry(ServerEndpoint.ID_ATTRIBUTE_NAME, requestId));
    return span;
  }

  public String expectedServerSpanName(ServerEndpoint endpoint, String method) {
    String route = expectedHttpRoute(endpoint);
    return route == null ? "HTTP " + method : route;
  }

  public String expectedHttpRoute(ServerEndpoint endpoint) {
    // no need to compute route if we're not expecting it
    if (!options.httpAttributes.apply(endpoint).contains(SemanticAttributes.HTTP_ROUTE)) {
      return null;
    }

    switch (endpoint) {
      case NOT_FOUND:
        return null;
      case PATH_PARAM:
        return options.contextPath + "/path/:id/param";
      default:
        return endpoint.resolvePath(address).getPath();
    }
  }

  final void setTesting(InstrumentationTestRunner testing, WebClient client, int port) {
    setTesting(testing, client, port, null);
  }

  final void setTesting(
      InstrumentationTestRunner testing, WebClient client, int port, URI address) {
    this.testing = testing;
    this.client = client;
    this.port = port;
    this.address = address;
  }
}
