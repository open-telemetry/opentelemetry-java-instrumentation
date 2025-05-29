/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.resolver.AddressResolverGroup;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.netty.http.client.HttpClient;

abstract class AbstractReactorNettyHttpClientTest
    extends AbstractHttpClientTest<HttpClient.ResponseReceiver<?>> {

  static final String USER_AGENT = "ReactorNetty";

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  protected abstract HttpClient createHttpClient();

  protected AddressResolverGroup<InetSocketAddress> getAddressResolverGroup() {
    return CustomNameResolverGroup.INSTANCE;
  }

  @Override
  public HttpClient.ResponseReceiver<?> buildRequest(
      String method, URI uri, Map<String, String> headers) {
    HttpClient client =
        createHttpClient()
            .followRedirect(true)
            .headers(h -> headers.forEach(h::add))
            .baseUrl(resolveAddress("").toString());
    if (uri.toString().contains("/read-timeout")) {
      client = client.responseTimeout(READ_TIMEOUT);
    }
    return client.request(HttpMethod.valueOf(method)).uri(uri.toString());
  }

  @Override
  public int sendRequest(
      HttpClient.ResponseReceiver<?> request, String method, URI uri, Map<String, String> headers) {
    return request
        .responseSingle(
            (resp, content) -> {
              // Make sure to consume content since that's when we close the span.
              return content.map(unused -> resp);
            })
        .block()
        .status()
        .code();
  }

  @Override
  public void sendRequestWithCallback(
      HttpClient.ResponseReceiver<?> request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    request
        .responseSingle(
            (resp, content) -> {
              // Make sure to consume content since that's when we close the span.
              return content.map(unused -> resp);
            })
        .subscribe(
            response -> httpClientResult.complete(response.status().code()),
            httpClientResult::complete);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.markAsLowLevelInstrumentation();
    optionsBuilder.setMaxRedirects(52);
    optionsBuilder.spanEndsAfterBody();

    // TODO: remove this test altogether? this scenario is (was) only implemented in reactor-netty,
    // all other HTTP clients worked in a different way
    //    optionsBuilder.enableTestCallbackWithImplicitParent();

    optionsBuilder.setClientSpanErrorMapper(
        (uri, exception) -> {
          if (exception.getClass().getName().endsWith("ReactiveException")) {
            // unopened port or non routable address
            if ("http://localhost:61/".equals(uri.toString())
                || "https://192.0.2.1/".equals(uri.toString())) {
              exception = exception.getCause();
            }
          }
          return exception;
        });

    optionsBuilder.setHttpAttributes(this::getHttpAttributes);
  }

  protected Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);

    // unopened port or non routable address
    if ("http://localhost:61/".equals(uri.toString())
        || "https://192.0.2.1/".equals(uri.toString())) {
      attributes.remove(NETWORK_PROTOCOL_VERSION);
    }

    if (uri.toString().contains("/read-timeout")) {
      attributes.remove(NETWORK_PROTOCOL_VERSION);
      attributes.remove(SERVER_ADDRESS);
      attributes.remove(SERVER_PORT);
    }
    return attributes;
  }

  @Test
  void shouldExposeContextToHttpClientCallbacks() throws InterruptedException {
    AtomicReference<Span> onRequestSpan = new AtomicReference<>();
    AtomicReference<Span> afterRequestSpan = new AtomicReference<>();
    AtomicReference<Span> onResponseSpan = new AtomicReference<>();
    AtomicReference<Span> afterResponseSpan = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    HttpClient httpClient =
        createHttpClient()
            .doOnRequest((rq, con) -> onRequestSpan.set(Span.current()))
            .doAfterRequest((rq, con) -> afterRequestSpan.set(Span.current()))
            .doOnResponse((rs, con) -> onResponseSpan.set(Span.current()))
            .doAfterResponseSuccess(
                (rs, con) -> {
                  afterResponseSpan.set(Span.current());
                  latch.countDown();
                });

    testing.runWithSpan(
        "parent",
        () ->
            httpClient
                .baseUrl(resolveAddress("").toString())
                .get()
                .uri("/success")
                .responseSingle(
                    (resp, content) -> {
                      // Make sure to consume content since that's when we close the span.
                      return content.map(unused -> resp);
                    })
                .block());

    latch.await(10, TimeUnit.SECONDS);

    testing.waitAndAssertTraces(
        trace -> {
          SpanData parentSpan = trace.getSpan(0);
          SpanData nettyClientSpan = trace.getSpan(1);

          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
              span -> span.hasName("GET").hasKind(CLIENT).hasParent(parentSpan),
              span -> span.hasName("test-http-server").hasKind(SERVER).hasParent(nettyClientSpan));

          assertSameSpan(parentSpan, onRequestSpan);
          assertSameSpan(nettyClientSpan, afterRequestSpan);
          assertSameSpan(nettyClientSpan, onResponseSpan);
          assertSameSpan(parentSpan, afterResponseSpan);
        });
  }

  @Test
  void shouldExposeContextToHttpRequestErrorCallback() {
    AtomicReference<Span> onRequestErrorSpan = new AtomicReference<>();

    HttpClient httpClient =
        createHttpClient().doOnRequestError((rq, err) -> onRequestErrorSpan.set(Span.current()));

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        httpClient
                            .get()
                            .uri("http://localhost:" + PortUtils.UNUSABLE_PORT + "/")
                            .response()
                            .block()));

    testing.waitAndAssertTraces(
        trace -> {
          SpanData parentSpan = trace.getSpan(0);

          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("parent")
                      .hasKind(INTERNAL)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasException(thrown),
              span ->
                  span.hasKind(CLIENT)
                      .hasParent(parentSpan)
                      .hasStatus(StatusData.error())
                      .hasException(thrown.getCause()));

          assertSameSpan(parentSpan, onRequestErrorSpan);
        });
  }

  @Test
  void shouldNotLeakConnections() {
    HashSet<Integer> uniqueChannelHashes = new HashSet<>();
    HttpClient httpClient =
        createHttpClient().doOnConnect(config -> uniqueChannelHashes.add(config.channelHash()));

    int count = 100;
    IntStream.range(0, count)
        .forEach(
            i ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      int status =
                          httpClient
                              .get()
                              .uri(resolveAddress("/success"))
                              .responseSingle(
                                  (resp, content) -> {
                                    // Make sure to consume content since that's when we close the
                                    // span.
                                    return content.map(unused -> resp);
                                  })
                              .block()
                              .status()
                              .code();
                      assertThat(status).isEqualTo(200);
                    }));

    testing.waitForTraces(count);
    assertThat(uniqueChannelHashes).hasSize(1);
  }

  @Test
  void shouldEndSpanOnMonoTimeout() {
    HttpClient httpClient = createHttpClient();

    URI uri = resolveAddress("/read-timeout");
    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        httpClient
                            .get()
                            .uri(uri)
                            .responseSingle(
                                (resp, content) -> {
                                  // Make sure to consume content since that's when we close the
                                  // span.
                                  return content.map(unused -> resp);
                                })
                            // apply Mono timeout that is way shorter than HTTP request timeout
                            .timeout(Duration.ofSeconds(1))
                            .block()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_FULL, uri.toString()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, uri.getPort()),
                            equalTo(ERROR_TYPE, "cancelled")),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))));
  }

  private static void assertSameSpan(SpanData expected, AtomicReference<Span> actual) {
    SpanContext expectedSpanContext = expected.getSpanContext();
    SpanContext actualSpanContext = actual.get().getSpanContext();
    assertThat(actualSpanContext.getTraceId()).isEqualTo(expectedSpanContext.getTraceId());
    assertThat(actualSpanContext.getSpanId()).isEqualTo(expectedSpanContext.getSpanId());
  }
}
