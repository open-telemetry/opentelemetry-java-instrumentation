/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.codec.http.HttpMethod;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.netty.http.client.HttpClient;

abstract class AbstractReactorNettyHttpClientTest
    extends AbstractHttpClientTest<HttpClient.ResponseReceiver<?>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  abstract HttpClient createHttpClient(boolean readTimeout);

  @Override
  public HttpClient.ResponseReceiver<?> buildRequest(
      String method, URI uri, Map<String, String> headers) {
    boolean readTimeout = uri.toString().contains("/read-timeout");
    return createHttpClient(readTimeout)
        .followRedirect(true)
        .headers(h -> headers.forEach(h::add))
        .baseUrl(resolveAddress("").toString())
        .request(HttpMethod.valueOf(method))
        .uri(uri.toString());
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
    optionsBuilder.disableTestRedirects();
    optionsBuilder.spanEndsAfterBody();

    optionsBuilder.setExpectedClientSpanNameMapper(
        (uri, method) -> {
          switch (uri.toString()) {
            case "http://localhost:61/": // unopened port
            case "https://192.0.2.1/": // non routable address
              return "CONNECT";
            default:
              return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(
                  uri, method);
          }
        });

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

    optionsBuilder.setHttpAttributes(
        uri -> {
          // unopened port or non routable address
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())) {
            return emptySet();
          }

          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(SERVER_ADDRESS);
          attributes.remove(SERVER_PORT);
          return attributes;
        });
  }

  @Test
  void shouldExposeContextToHttpClientCallbacks() throws InterruptedException {
    AtomicReference<Span> onRequestSpan = new AtomicReference<>();
    AtomicReference<Span> afterRequestSpan = new AtomicReference<>();
    AtomicReference<Span> onResponseSpan = new AtomicReference<>();
    AtomicReference<Span> afterResponseSpan = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    HttpClient httpClient =
        createHttpClient(false)
            .doOnRequest((rq, con) -> onRequestSpan.set(Span.current()))
            .doAfterRequest((rq, con) -> afterRequestSpan.set(Span.current()))
            .doOnResponse((rs, con) -> onResponseSpan.set(Span.current()))
            .doAfterResponse(
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
        createHttpClient(false)
            .doOnRequestError((rq, err) -> onRequestErrorSpan.set(Span.current()));

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        httpClient
                            .get()
                            .uri("http://localhost:$UNUSABLE_PORT/")
                            .responseSingle(
                                (resp, content) -> {
                                  // Make sure to consume content since that's when we close the
                                  // span.
                                  return content.map(unused -> resp);
                                })
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

  private static void assertSameSpan(SpanData expected, AtomicReference<Span> actual) {
    SpanContext expectedSpanContext = expected.getSpanContext();
    SpanContext actualSpanContext = actual.get().getSpanContext();
    assertThat(actualSpanContext.getTraceId()).isEqualTo(expectedSpanContext.getTraceId());
    assertThat(actualSpanContext.getSpanId()).isEqualTo(expectedSpanContext.getSpanId());
  }
}
