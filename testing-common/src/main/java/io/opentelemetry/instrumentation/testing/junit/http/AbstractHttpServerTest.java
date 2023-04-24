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
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.NetAttributes;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.assertj.core.api.AssertAccess;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractHttpServerTest<SERVER> extends AbstractHttpServerUsingTest<SERVER> {
  public static final String TEST_REQUEST_HEADER = "X-Test-Request";
  public static final String TEST_RESPONSE_HEADER = "X-Test-Response";

  private final HttpServerTestOptions options = new HttpServerTestOptions();

  @BeforeAll
  void setupOptions() {
    options.expectedServerSpanNameMapper = this::expectedServerSpanName;
    options.expectedHttpRoute = this::expectedHttpRoute;

    configure(options);

    startServer();
  }

  @AfterAll
  void cleanup() {
    cleanupServer();
  }

  @Override
  protected final String getContextPath() {
    return options.contextPath;
  }

  protected void configure(HttpServerTestOptions options) {}

  public static <T> T controller(ServerEndpoint endpoint, Supplier<T> closure) {
    assert Span.current().getSpanContext().isValid() : "Controller should have a parent span.";
    if (endpoint == NOT_FOUND) {
      return closure.get();
    }
    return GlobalTraceUtil.runWithSpan("controller", () -> closure.get());
  }

  protected AggregatedHttpRequest request(ServerEndpoint uri, String method) {
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
      assertResponseHasCustomizedHeaders(response, SUCCESS, null);
    }

    assertTheTraces(count, null, null, null, method, SUCCESS, responses.get(0));
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

    String spanId = assertResponseHasCustomizedHeaders(response, SUCCESS, traceId);
    assertTheTraces(1, traceId, parentId, spanId, "GET", SUCCESS, response);
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

    String spanId = assertResponseHasCustomizedHeaders(response, SUCCESS, traceId);
    assertTheTraces(1, traceId, parentId, spanId, "GET", SUCCESS, response);
  }

  @ParameterizedTest
  @MethodSource("provideServerEndpoints")
  void requestWithQueryString(ServerEndpoint endpoint) {
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(endpoint.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(endpoint.getBody());

    String spanId = assertResponseHasCustomizedHeaders(response, endpoint, null);
    assertTheTraces(1, null, null, spanId, method, endpoint, response);
  }

  private static Stream<ServerEndpoint> provideServerEndpoints() {
    return Stream.of(ServerEndpoint.SUCCESS, ServerEndpoint.QUERY_PARAM);
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

    String spanId = assertResponseHasCustomizedHeaders(response, REDIRECT, null);
    assertTheTraces(1, null, null, spanId, method, REDIRECT, response);
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

    String spanId = assertResponseHasCustomizedHeaders(response, ERROR, null);
    assertTheTraces(1, null, null, spanId, method, ERROR, response);
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

      String spanId = assertResponseHasCustomizedHeaders(response, EXCEPTION, null);
      assertTheTraces(1, null, null, spanId, method, EXCEPTION, response);
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

    String spanId = assertResponseHasCustomizedHeaders(response, NOT_FOUND, null);
    assertTheTraces(1, null, null, spanId, method, NOT_FOUND, response);
  }

  @Test
  void requestWithPathParameter() {
    assumeTrue(options.testPathParam);

    String method = "GET";
    AggregatedHttpRequest request = request(PATH_PARAM, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(PATH_PARAM.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(PATH_PARAM.getBody());

    String spanId = assertResponseHasCustomizedHeaders(response, PATH_PARAM, null);
    assertTheTraces(1, null, null, spanId, method, PATH_PARAM, response);
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
    assertThat(response.headers().get("X-Test-Response")).isEqualTo("test");

    String spanId = assertResponseHasCustomizedHeaders(response, CAPTURE_HEADERS, null);
    assertTheTraces(1, null, null, spanId, "GET", CAPTURE_HEADERS, response);
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

    String spanId = assertResponseHasCustomizedHeaders(response, CAPTURE_PARAMETERS, null);
    assertTheTraces(1, null, null, spanId, "POST", CAPTURE_PARAMETERS, response);
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.longKey(ServerEndpoint.ID_ATTRIBUTE_NAME),
                                requestId)));
            spanAssertions.add(
                span -> assertIndexedServerSpan(span, requestId).hasParent(rootSpan));

            if (options.hasHandlerSpan.test(endpoint)) {
              spanAssertions.add(
                  span -> assertHandlerSpan(span, "GET", endpoint).hasParent(trace.getSpan(1)));
            }

            int parentIndex = spanAssertions.size() - 2;
            if (options.hasHandlerAsControllerParentSpan.test(endpoint)) {
              parentIndex = parentIndex + 1;
            }
            int finalParentIndex = parentIndex;
            spanAssertions.add(
                span ->
                    assertIndexedControllerSpan(span, requestId)
                        .hasParent(trace.getSpan(finalParentIndex)));

            trace.hasSpansSatisfyingExactly(spanAssertions);
          });
    }
    testing.waitAndAssertTraces(assertions);
  }

  protected String assertResponseHasCustomizedHeaders(
      AggregatedHttpResponse response, ServerEndpoint endpoint, String expectedTraceId) {
    if (!options.hasResponseCustomizer.test(endpoint)) {
      return null;
    }

    String responseHeaderTraceId = response.headers().get("x-test-traceid");
    String responseHeaderSpanId = response.headers().get("x-test-spanid");

    if (expectedTraceId != null) {
      assertThat(responseHeaderTraceId).matches(expectedTraceId);
    } else {
      assertThat(responseHeaderTraceId).isNotNull();
    }

    assertThat(responseHeaderSpanId).isNotNull();
    return responseHeaderSpanId;
  }

  // NOTE: this method does not currently implement asserting all the span types that groovy
  // HttpServerTest does
  protected void assertTheTraces(
      int size,
      String traceId,
      String parentId,
      String spanId,
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
                  if (traceId != null) {
                    span.hasTraceId(traceId);
                  }
                  if (spanId != null) {
                    span.hasSpanId(spanId);
                  }
                  if (parentId != null) {
                    span.hasParentSpanId(parentId);
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
              int parentIndex = 0;
              if (options.hasHandlerSpan.test(endpoint)
                  && options.hasHandlerAsControllerParentSpan.test(endpoint)) {
                parentIndex = spanAssertions.size() - 1;
              }
              int finalParentIndex = parentIndex;
              spanAssertions.add(
                  span -> {
                    assertControllerSpan(
                        span, endpoint == EXCEPTION ? options.expectedException : null);
                    span.hasParent(trace.getSpan(finalParentIndex));
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

            if (options.verifyServerSpanEndTime) {
              List<SpanData> spanData = AssertAccess.getActual(trace);
              if (spanData.size() > 1) {
                SpanData rootSpan = spanData.get(0);
                for (int j = 1; j < spanData.size(); j++) {
                  assertThat(rootSpan.getEndEpochNanos())
                      .isGreaterThanOrEqualTo(spanData.get(j).getEndEpochNanos());
                }
              }
            }
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
    String name = getString(method, endpoint, expectedRoute);

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

          assertThat(attrs).containsEntry(SemanticAttributes.NET_HOST_NAME, "localhost");
          // TODO: Move to test knob rather than always treating as optional
          // TODO: once httpAttributes test knob is used, verify default port values
          if (attrs.get(SemanticAttributes.NET_HOST_PORT) != null) {
            assertThat(attrs).containsEntry(SemanticAttributes.NET_HOST_PORT, port);
          }
          if (attrs.get(SemanticAttributes.NET_SOCK_PEER_PORT) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.NET_SOCK_PEER_PORT,
                    value ->
                        assertThat(value)
                            .isInstanceOf(Long.class)
                            .isNotEqualTo(Long.valueOf(port)));
          }
          if (attrs.get(SemanticAttributes.NET_SOCK_PEER_ADDR) != null) {
            assertThat(attrs)
                .containsEntry(
                    SemanticAttributes.NET_SOCK_PEER_ADDR, options.sockPeerAddr.apply(endpoint));
          }
          if (attrs.get(SemanticAttributes.NET_SOCK_HOST_ADDR) != null) {
            assertThat(attrs).containsEntry(SemanticAttributes.NET_SOCK_HOST_ADDR, "127.0.0.1");
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

          if (attrs.get(NetAttributes.NET_PROTOCOL_NAME) != null) {
            assertThat(attrs).containsEntry(NetAttributes.NET_PROTOCOL_NAME, "http");
          }
          if (attrs.get(NetAttributes.NET_PROTOCOL_VERSION) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    NetAttributes.NET_PROTOCOL_VERSION,
                    entry -> assertThat(entry).isIn("1.1", "2.0"));
          }
          assertThat(attrs).containsEntry(SemanticAttributes.USER_AGENT_ORIGINAL, TEST_USER_AGENT);

          assertThat(attrs).containsEntry(SemanticAttributes.HTTP_SCHEME, "http");
          if (endpoint != INDEXED_CHILD) {
            assertThat(attrs)
                .containsEntry(
                    SemanticAttributes.HTTP_TARGET,
                    endpoint.resolvePath(address).getPath()
                        + (endpoint == QUERY_PARAM ? "?" + endpoint.body : ""));
          }

          if (attrs.get(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                    entry -> assertThat(entry).isNotNegative());
          }
          if (attrs.get(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                    entry -> assertThat(entry).isNotNegative());
          }
          if (httpAttributes.contains(SemanticAttributes.HTTP_ROUTE) && expectedRoute != null) {
            assertThat(attrs).containsEntry(SemanticAttributes.HTTP_ROUTE, expectedRoute);
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

  private String getString(String method, ServerEndpoint endpoint, String expectedRoute) {
    String name = options.expectedServerSpanNameMapper.apply(endpoint, method, expectedRoute);
    return name;
  }

  protected SpanDataAssert assertIndexedServerSpan(SpanDataAssert span, int requestId) {
    ServerEndpoint endpoint = INDEXED_CHILD;
    String method = "GET";
    assertServerSpan(span, method, endpoint);

    span.hasAttributesSatisfying(
        equalTo(
            SemanticAttributes.HTTP_TARGET,
            endpoint.resolvePath(address).getPath() + "?id=" + requestId));

    return span;
  }

  protected SpanDataAssert assertIndexedControllerSpan(SpanDataAssert span, int requestId) {
    span.hasName("controller")
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(AttributeKey.longKey(ServerEndpoint.ID_ATTRIBUTE_NAME), requestId));
    return span;
  }

  public String expectedServerSpanName(
      ServerEndpoint endpoint, String method, @Nullable String route) {
    return HttpServerTestOptions.DEFAULT_EXPECTED_SERVER_SPAN_NAME_MAPPER.apply(
        endpoint, method, route);
  }

  public String expectedHttpRoute(ServerEndpoint endpoint) {
    // no need to compute route if we're not expecting it
    if (!options.httpAttributes.apply(endpoint).contains(SemanticAttributes.HTTP_ROUTE)) {
      return null;
    }

    if (NOT_FOUND.equals(endpoint)) {
      return null;
    } else if (PATH_PARAM.equals(endpoint)) {
      return options.contextPath + "/path/:id/param";
    } else {
      return endpoint.resolvePath(address).getPath();
    }
  }
}
