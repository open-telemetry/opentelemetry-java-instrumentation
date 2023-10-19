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
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.HttpRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpRequestBuilder;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import io.opentelemetry.testing.internal.io.netty.bootstrap.Bootstrap;
import io.opentelemetry.testing.internal.io.netty.buffer.Unpooled;
import io.opentelemetry.testing.internal.io.netty.channel.Channel;
import io.opentelemetry.testing.internal.io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.testing.internal.io.netty.channel.ChannelInitializer;
import io.opentelemetry.testing.internal.io.netty.channel.ChannelOption;
import io.opentelemetry.testing.internal.io.netty.channel.ChannelPipeline;
import io.opentelemetry.testing.internal.io.netty.channel.EventLoopGroup;
import io.opentelemetry.testing.internal.io.netty.channel.SimpleChannelInboundHandler;
import io.opentelemetry.testing.internal.io.netty.channel.nio.NioEventLoopGroup;
import io.opentelemetry.testing.internal.io.netty.channel.socket.SocketChannel;
import io.opentelemetry.testing.internal.io.netty.channel.socket.nio.NioSocketChannel;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.DefaultHttpResponse;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpClientCodec;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpObject;
import io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpVersion;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    assertTheTraces(count, null, null, null, method, SUCCESS);
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
    assertTheTraces(1, traceId, parentId, spanId, "GET", SUCCESS);
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
    assertTheTraces(1, traceId, parentId, spanId, "GET", SUCCESS);
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
    assertTheTraces(1, null, null, spanId, method, endpoint);
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
    assertTheTraces(1, null, null, spanId, method, REDIRECT);
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
    assertTheTraces(1, null, null, spanId, method, ERROR);
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
      assertTheTraces(1, null, null, spanId, method, EXCEPTION);
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
    assertTheTraces(1, null, null, spanId, method, NOT_FOUND);
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
    assertTheTraces(1, null, null, spanId, method, PATH_PARAM);
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
    assertTheTraces(1, null, null, spanId, "GET", CAPTURE_HEADERS);
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
    assertTheTraces(1, null, null, spanId, "POST", CAPTURE_PARAMETERS);
  }

  @Test
  void httpServerMetrics() {
    String method = "GET";
    AggregatedHttpRequest request = request(SUCCESS, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.anySatisfy(
              spanData -> assertServerSpan(assertThat(spanData), method, SUCCESS, SUCCESS.status));
        });

    String durationInstrumentName =
        SemconvStability.emitStableHttpSemconv()
            ? "http.server.request.duration"
            : "http.server.duration";
    String durationInstrumentDescription =
        SemconvStability.emitStableHttpSemconv()
            ? "Duration of HTTP server requests."
            : "The duration of the inbound HTTP request";

    String metricsInstrumentationName = options.metricsInstrumentationName.get();
    if (metricsInstrumentationName == null) {
      metricsInstrumentationName = instrumentationName.get();
    }
    testing.waitAndAssertMetrics(
        metricsInstrumentationName,
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

  @Test
  void httpPipelining() throws InterruptedException {
    assumeTrue(options.testHttpPipelining);

    int count = 10;
    CountDownLatch countDownLatch = new CountDownLatch(count);
    ServerEndpoint endpoint = INDEXED_CHILD;
    TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    TextMapSetter<DefaultFullHttpRequest> setter =
        (request, key, value) -> request.headers().set(key, value);

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    try {
      Bootstrap bootstrap = buildBootstrap(eventLoopGroup);
      Channel channel = bootstrap.connect(address.getHost(), port).sync().channel();
      channel
          .pipeline()
          .addLast(
              new SimpleChannelInboundHandler<HttpObject>() {

                @Override
                protected void channelRead0(
                    ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
                  if (httpObject instanceof DefaultHttpResponse) {
                    countDownLatch.countDown();
                  }
                }
              });

      for (int i = 0; i < count; i++) {
        int index = i;
        String target =
            endpoint.resolvePath(address).getPath().toString()
                + "?"
                + ServerEndpoint.ID_PARAMETER_NAME
                + "="
                + index;

        testing.runWithSpan(
            "client " + index,
            () -> {
              Span.current().setAttribute(ServerEndpoint.ID_ATTRIBUTE_NAME, index);
              DefaultFullHttpRequest request =
                  new DefaultFullHttpRequest(
                      HttpVersion.HTTP_1_1,
                      io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpMethod
                          .valueOf("GET"),
                      target,
                      Unpooled.EMPTY_BUFFER);
              request.headers().set(HttpHeaderNames.HOST, address.getHost() + ":" + port);
              request.headers().set(HttpHeaderNames.USER_AGENT, TEST_USER_AGENT);
              request.headers().set(HttpHeaderNames.X_FORWARDED_FOR, TEST_CLIENT_IP);

              propagator.inject(Context.current(), request, setter);
              channel.writeAndFlush(request);
            });
      }

      countDownLatch.await(30, TimeUnit.SECONDS);
      assertHighConcurrency(count);
    } finally {
      eventLoopGroup.shutdownGracefully().await(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void requestWithNonStandardHttpMethod() throws InterruptedException {
    assumeTrue(SemconvStability.emitStableHttpSemconv());
    assumeTrue(options.testNonStandardHttpMethod);

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    try {
      Bootstrap bootstrap = buildBootstrap(eventLoopGroup);
      Channel channel = bootstrap.connect(address.getHost(), port).sync().channel();

      String method = "TEST";
      DefaultFullHttpRequest request =
          new DefaultFullHttpRequest(
              HttpVersion.HTTP_1_1,
              new io.opentelemetry.testing.internal.io.netty.handler.codec.http.HttpMethod(method),
              SUCCESS.resolvePath(address).getPath(),
              Unpooled.EMPTY_BUFFER);
      request.headers().set(HttpHeaderNames.HOST, address.getHost() + ":" + port);
      request.headers().set(HttpHeaderNames.USER_AGENT, TEST_USER_AGENT);
      request.headers().set(HttpHeaderNames.X_FORWARDED_FOR, TEST_CLIENT_IP);

      testing
          .getOpenTelemetry()
          .getPropagators()
          .getTextMapPropagator()
          .inject(
              Context.current(),
              request,
              (carrier, key, value) -> carrier.headers().set(key, value));
      channel.writeAndFlush(request);

      // TODO: add stricter assertions; could be difficult with the groovy code still in place
      // though
      testing.waitAndAssertTraces(
          trace ->
              trace.anySatisfy(
                  span ->
                      assertServerSpan(
                              assertThat(span),
                              HttpConstants._OTHER,
                              SUCCESS,
                              options.responseCodeOnNonStandardHttpMethod)
                          .hasAttribute(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method)));
    } finally {
      eventLoopGroup.shutdownGracefully().await(10, TimeUnit.SECONDS);
    }
  }

  private static Bootstrap buildBootstrap(EventLoopGroup eventLoopGroup) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(10))
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new HttpClientCodec());
              }
            });
    return bootstrap;
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
      ServerEndpoint endpoint) {
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      assertions.add(
          trace -> {
            List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
            spanAssertions.add(
                span -> {
                  assertServerSpan(span, method, endpoint, endpoint.status);
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
              if (options.hasHandlerSpan.test(endpoint)) {
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

  @CanIgnoreReturnValue
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

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected SpanDataAssert assertServerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint, int statusCode) {

    Set<AttributeKey<?>> httpAttributes = options.httpAttributes.apply(endpoint);
    String expectedRoute = options.expectedHttpRoute.apply(endpoint, method);
    String name = options.expectedServerSpanNameMapper.apply(endpoint, method, expectedRoute);

    span.hasName(name).hasKind(SpanKind.SERVER);
    if (statusCode >= 500) {
      span.hasStatus(StatusData.error());
    }

    if (endpoint == EXCEPTION && options.hasExceptionOnServerSpan.test(endpoint)) {
      span.hasException(options.expectedException);
    }

    span.hasAttributesSatisfying(
        attrs -> {
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

          assertThat(attrs)
              .containsEntry(getAttributeKey(SemanticAttributes.NET_HOST_NAME), "localhost");
          // TODO: Move to test knob rather than always treating as optional
          // TODO: once httpAttributes test knob is used, verify default port values
          AttributeKey<Long> netHostPortKey = getAttributeKey(SemanticAttributes.NET_HOST_PORT);
          if (attrs.get(netHostPortKey) != null) {
            assertThat(attrs).containsEntry(netHostPortKey, port);
          }
          AttributeKey<Long> netSockPeerPortKey =
              getAttributeKey(SemanticAttributes.NET_SOCK_PEER_PORT);
          if (attrs.get(netSockPeerPortKey) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    netSockPeerPortKey,
                    value ->
                        assertThat(value)
                            .isInstanceOf(Long.class)
                            .isNotEqualTo(Long.valueOf(port)));
          }
          AttributeKey<String> netSockPeerAddrKey =
              getAttributeKey(SemanticAttributes.NET_SOCK_PEER_ADDR);
          if (attrs.get(netSockPeerAddrKey) != null) {
            assertThat(attrs)
                .containsEntry(netSockPeerAddrKey, options.sockPeerAddr.apply(endpoint));
          }
          AttributeKey<String> netSockHostAddrKey =
              getAttributeKey(SemanticAttributes.NET_SOCK_PEER_ADDR);
          if (attrs.get(netSockHostAddrKey) != null) {
            assertThat(attrs).containsEntry(netSockHostAddrKey, "127.0.0.1");
          }

          assertThat(attrs)
              .hasEntrySatisfying(
                  getAttributeKey(SemanticAttributes.HTTP_CLIENT_IP),
                  entry ->
                      assertThat(entry)
                          .satisfiesAnyOf(
                              value -> assertThat(value).isNull(),
                              value -> assertThat(value).isEqualTo(TEST_CLIENT_IP)));
          if (SemconvStability.emitStableHttpSemconv()
              && attrs.get(SemanticAttributes.CLIENT_PORT) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    SemanticAttributes.CLIENT_PORT, port -> assertThat(port).isGreaterThan(0));
          }
          assertThat(attrs).containsEntry(getAttributeKey(SemanticAttributes.HTTP_METHOD), method);

          assertThat(attrs)
              .containsEntry(getAttributeKey(SemanticAttributes.HTTP_STATUS_CODE), statusCode);
          if (statusCode >= 500 && SemconvStability.emitStableHttpSemconv()) {
            assertThat(attrs).containsEntry(HttpAttributes.ERROR_TYPE, String.valueOf(statusCode));
          }

          AttributeKey<String> netProtocolKey =
              getAttributeKey(SemanticAttributes.NET_PROTOCOL_NAME);
          if (attrs.get(netProtocolKey) != null) {
            assertThat(attrs).containsEntry(netProtocolKey, "http");
          }
          AttributeKey<String> netProtocolVersionKey =
              getAttributeKey(SemanticAttributes.NET_PROTOCOL_VERSION);
          if (attrs.get(netProtocolVersionKey) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    netProtocolVersionKey, entry -> assertThat(entry).isIn("1.1", "2.0"));
          }
          assertThat(attrs).containsEntry(SemanticAttributes.USER_AGENT_ORIGINAL, TEST_USER_AGENT);

          assertThat(attrs).containsEntry(getAttributeKey(SemanticAttributes.HTTP_SCHEME), "http");
          if (endpoint != INDEXED_CHILD) {
            if (SemconvStability.emitOldHttpSemconv()) {
              assertThat(attrs)
                  .containsEntry(
                      SemanticAttributes.HTTP_TARGET,
                      endpoint.resolvePath(address).getPath()
                          + (endpoint.getQuery() != null ? "?" + endpoint.getQuery() : ""));
            }
            if (SemconvStability.emitStableHttpSemconv()) {
              assertThat(attrs)
                  .containsEntry(
                      SemanticAttributes.URL_PATH, endpoint.resolvePath(address).getPath());
              if (endpoint.getQuery() != null) {
                assertThat(attrs).containsEntry(SemanticAttributes.URL_QUERY, endpoint.getQuery());
              }
            }
          }

          AttributeKey<Long> httpRequestLengthKey =
              getAttributeKey(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH);
          if (attrs.get(httpRequestLengthKey) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    httpRequestLengthKey, entry -> assertThat(entry).isNotNegative());
          }
          AttributeKey<Long> httpResponseLengthKey =
              getAttributeKey(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH);
          if (attrs.get(httpResponseLengthKey) != null) {
            assertThat(attrs)
                .hasEntrySatisfying(
                    httpResponseLengthKey, entry -> assertThat(entry).isNotNegative());
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

  protected static <T> AttributeKey<T> getAttributeKey(AttributeKey<T> oldKey) {
    return SemconvStabilityUtil.getAttributeKey(oldKey);
  }

  @CanIgnoreReturnValue
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected SpanDataAssert assertIndexedServerSpan(SpanDataAssert span, int requestId) {
    ServerEndpoint endpoint = INDEXED_CHILD;
    String method = "GET";
    assertServerSpan(span, method, endpoint, endpoint.status);

    if (SemconvStability.emitOldHttpSemconv()) {
      span.hasAttributesSatisfying(
          equalTo(
              SemanticAttributes.HTTP_TARGET,
              endpoint.resolvePath(address).getPath() + "?id=" + requestId));
    }
    if (SemconvStability.emitStableHttpSemconv()) {
      span.hasAttributesSatisfying(
          equalTo(SemanticAttributes.URL_PATH, endpoint.resolvePath(address).getPath()));
      span.hasAttributesSatisfying(equalTo(SemanticAttributes.URL_QUERY, "id=" + requestId));
    }

    return span;
  }

  @CanIgnoreReturnValue
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

  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    // no need to compute route if we're not expecting it
    if (!options.httpAttributes.apply(endpoint).contains(SemanticAttributes.HTTP_ROUTE)) {
      return null;
    }

    if (HttpConstants._OTHER.equals(method)) {
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
