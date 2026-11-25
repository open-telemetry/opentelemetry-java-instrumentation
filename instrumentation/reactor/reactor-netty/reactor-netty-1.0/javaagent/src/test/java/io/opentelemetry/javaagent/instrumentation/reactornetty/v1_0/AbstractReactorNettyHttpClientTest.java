/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.instrumentation.testing.util.TestServerServiceMapping.expectedMappedServiceName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.resolver.AddressResolverGroup;
import io.opentelemetry.api.common.AttributeKey;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    if (uri.getPath().endsWith("/read-timeout")) {
      client = client.responseTimeout(READ_TIMEOUT);
    }
    return client.request(HttpMethod.valueOf(method)).uri(uri.toString());
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
    optionsBuilder.markAsLowLevelInstrumentation();
    optionsBuilder.setMaxRedirects(52);
    optionsBuilder.spanEndsAfterBody();

    // TODO: remove this test altogether? this scenario is (was) only implemented in reactor-netty,
    // all other HTTP clients worked in a different way
    //    optionsBuilder.enableTestCallbackWithImplicitParent();

    optionsBuilder.setClientSpanErrorMapper((uri, exception) -> {
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

    if (uri.getPath().endsWith("/read-timeout")) {
      attributes.remove(SERVER_ADDRESS);
      attributes.remove(SERVER_PORT);
    }
    return attributes;
  }

  @Test
  void shouldExposeContextToHttpClientCallbacks() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    HttpClient httpClient =
        createHttpClient()
            .doOnRequest((rq, con) -> testing.runWithSpan("onRequest", () -> {}))
            .doAfterRequest((rq, con) -> testing.runWithSpan("afterRequest", () -> {}))
            .doOnResponse((rs, con) -> testing.runWithSpan("onResponse", () -> {}))
            .doAfterResponseSuccess((rs, con) -> {
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
            ? createHttpClient()
                .doOnError(
                    (rq, err) -> testing.runWithSpan("requestError", () -> {}), (rs, err) -> {})
            : createHttpClient()
                .doOnRequestError((rq, err) -> testing.runWithSpan("requestError", () -> {}));

    Throwable thrown =
        catchThrowable(() ->
            testing.runWithSpan(
                "parent",
                () ->
                    httpClient
                        .get()
                        .uri("http://localhost:" + PortUtils.UNUSABLE_PORT + "/")
                        .response()
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

  @Test
  void shouldNotLeakConnections() {
    HashSet<Integer> uniqueChannelHashes = new HashSet<>();
    HttpClient httpClient =
        createHttpClient().doOnConnect(config -> uniqueChannelHashes.add(config.channelHash()));

    int count = 100;
    IntStream.range(0, count)
        .forEach(i ->
            testing.runWithSpan(
                "parent",
                () -> {
                  int status =
                      httpClient
                          .get()
                          .uri(resolveAddress("/success"))
                          .responseSingle((resp, content) -> {
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
  @SuppressWarnings("deprecation") // using deprecated semconv
  void shouldEndSpanOnMonoTimeout() {
    HttpClient httpClient = createHttpClient();

    URI uri = resolveAddress("/read-timeout");
    Throwable thrown =
        catchThrowable(() ->
            testing.runWithSpan(
                "parent",
                () ->
                    httpClient
                        .get()
                        .uri(uri)
                        .responseSingle((resp, content) -> {
                          // Make sure to consume content since that's when we close the
                          // span.
                          return content.map(unused -> resp);
                        })
                        // apply Mono timeout that is way shorter than HTTP request timeout
                        .timeout(Duration.ofSeconds(1))
                        .block()));

    testing.waitAndAssertTraces(trace ->
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
                        equalTo(ERROR_TYPE, "cancelled"),
                        equalTo(stringKey("server.service.name"), expectedMappedServiceName())),
            span ->
                span.hasName("test-http-server")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(1))));
  }
}
