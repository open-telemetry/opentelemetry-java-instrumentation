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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.codec.http.HttpMethod;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.netty.http.client.HttpClient;

abstract class AbstractReactorNettyHttpClientTest
    extends AbstractHttpClientTest<HttpClient.ResponseReceiver<?>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final String UNUSABLE_PORT_URI =
      "http://localhost:" + PortUtils.UNUSABLE_PORT + "/";

  abstract HttpClient createHttpClient(boolean readTimeout);

  @Override
  public HttpClient.ResponseReceiver<?> buildRequest(
      String method, URI uri, Map<String, String> headers) {
    boolean readTimeout = uri.getPath().endsWith("/read-timeout");
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
        .responseSingle((resp, content) -> {
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
        .responseSingle((resp, content) -> {
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

    optionsBuilder.setExpectedClientSpanNameMapper((uri, method) -> {
      switch (uri.toString()) {
        case UNUSABLE_PORT_URI: // unopened port
        case "https://192.0.2.1/": // non routable address
          return "CONNECT";
        default:
          return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
      }
    });

    optionsBuilder.setClientSpanErrorMapper((uri, exception) -> {
      if (exception.getClass().getName().endsWith("ReactiveException")) {
        // unopened port or non routable address
        if (UNUSABLE_PORT_URI.equals(uri.toString())
            || "https://192.0.2.1/".equals(uri.toString())) {
          exception = exception.getCause();
        }
      }
      return exception;
    });

    optionsBuilder.setHttpAttributes(uri -> {
      // unopened port or non routable address
      if (UNUSABLE_PORT_URI.equals(uri.toString()) || "https://192.0.2.1/".equals(uri.toString())) {
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
    CountDownLatch latch = new CountDownLatch(1);

    HttpClient httpClient =
        createHttpClient(false)
            .doOnRequest((rq, con) -> testing.runWithSpan("onRequest", () -> {}))
            .doAfterRequest((rq, con) -> testing.runWithSpan("afterRequest", () -> {}))
            .doOnResponse((rs, con) -> testing.runWithSpan("onResponse", () -> {}))
            .doAfterResponse((rs, con) -> {
              testing.runWithSpan("afterResponse", () -> {});
              latch.countDown();
            });

    testing.runWithSpan(
        "parent",
        () ->
            httpClient
                .baseUrl(resolveAddress("").toString())
                .get()
                .uri("/success")
                .responseSingle((resp, content) -> {
                  // Make sure to consume content since that's when we close the span.
                  return content.map(unused -> resp);
                })
                .block());

    assertThat(latch.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(trace -> {
      SpanData parentSpan = trace.getSpan(0);
      SpanData nettyClientSpan =
          trace.actual().stream()
              .filter(span -> span.getKind() == CLIENT)
              .findFirst()
              .orElseThrow(AssertionError::new);

      trace.hasSpansSatisfyingExactlyInAnyOrder(
          span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
          span -> span.hasName("GET").hasKind(CLIENT).hasParent(parentSpan),
          span -> span.hasName("test-http-server").hasKind(SERVER).hasParent(nettyClientSpan),
          span -> span.hasName("onRequest").hasParent(parentSpan),
          span -> span.hasName("afterRequest").hasParent(nettyClientSpan),
          span -> span.hasName("onResponse").hasParent(nettyClientSpan),
          span -> span.hasName("afterResponse").hasParent(parentSpan));
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldExposeContextToHttpRequestErrorCallback(boolean combinedCallbacks) {
    HttpClient httpClient =
        combinedCallbacks
            ? createHttpClient(false)
                .doOnError(
                    (rq, err) -> testing.runWithSpan("requestError", () -> {}), (rs, err) -> {})
            : createHttpClient(false)
                .doOnRequestError((rq, err) -> testing.runWithSpan("requestError", () -> {}));

    Throwable thrown =
        catchThrowable(() ->
            testing.runWithSpan(
                "parent",
                () ->
                    httpClient
                        .get()
                        .uri(UNUSABLE_PORT_URI)
                        .responseSingle((resp, content) -> {
                          // Make sure to consume content since that's when we close the
                          // span.
                          return content.map(unused -> resp);
                        })
                        .block()));

    testing.waitAndAssertTraces(trace -> {
      SpanData parentSpan = trace.getSpan(0);

      trace.hasSpansSatisfyingExactlyInAnyOrder(
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
                  .hasException(thrown.getCause()),
          span -> span.hasName("requestError").hasParent(parentSpan));
    });
  }
}
