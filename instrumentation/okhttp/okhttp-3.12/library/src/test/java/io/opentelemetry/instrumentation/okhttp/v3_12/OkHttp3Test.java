/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OkHttp3Test extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  private static final AttributeKey<Long> CALL_START = AttributeKey.longKey("http.call.start_time");
  private static final AttributeKey<Long> CALL_END = AttributeKey.longKey("http.call.end_time");
  private static final AttributeKey<Long> DNS_START = AttributeKey.longKey("http.dns.start_time");
  private static final AttributeKey<Long> DNS_END = AttributeKey.longKey("http.dns.end_time");
  private static final AttributeKey<Long> CONNECT_START =
      AttributeKey.longKey("http.connect.start_time");
  private static final AttributeKey<Long> CONNECT_END =
      AttributeKey.longKey("http.connect.end_time");
  private static final AttributeKey<Long> REQUEST_HEADERS_START =
      AttributeKey.longKey("http.request.headers.start_time");
  private static final AttributeKey<Long> REQUEST_HEADERS_END =
      AttributeKey.longKey("http.request.headers.end_time");
  private static final AttributeKey<Long> RESPONSE_HEADERS_START =
      AttributeKey.longKey("http.response.headers.start_time");
  private static final AttributeKey<Long> RESPONSE_HEADERS_END =
      AttributeKey.longKey("http.response.headers.end_time");
  private static final AttributeKey<Long> RESPONSE_BODY_START =
      AttributeKey.longKey("http.response.body.start_time");
  private static final AttributeKey<Long> RESPONSE_BODY_END =
      AttributeKey.longKey("http.response.body.end_time");

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    clientBuilder.protocols(singletonList(Protocol.HTTP_1_1));
    return OkHttpTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .createCallFactory(clientBuilder.build());
  }

  // Unlike the okhttp-3.0 network-interceptor instrumentation, the event-listener model produces a
  // single client span per logical request (covering all redirects), i.e. high-level
  // instrumentation, so this overrides AbstractOkHttp3Test#configure to drop the low-level marker.
  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setMaxRedirects(21); // 1st send + 20 retries
    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);

          // protocol is extracted from the response, and those URLs cause exceptions (= null
          // response)
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())
              || "http://192.0.2.1/".equals(uri.toString())
              || resolveAddress("/read-timeout").toString().equals(uri.toString())) {
            attributes.remove(NETWORK_PROTOCOL_VERSION);
          }

          return attributes;
        });
  }

  // build a call factory that never reuses a pooled connection so that the dns/connect timing
  // phases are always recorded
  private Call.Factory freshConnectionCallFactory() {
    OkHttpClient.Builder clientBuilder =
        new OkHttpClient.Builder().connectionPool(new ConnectionPool(0, 1, NANOSECONDS));
    return createCallFactory(clientBuilder);
  }

  @Test
  void timingAttributesOnSynchronousCallSpan() throws Exception {
    URI uri = resolveAddress("/success");
    Request request =
        new Request.Builder()
            .url(uri.toString())
            .post(RequestBody.create(MediaType.parse("text/plain"), "hello"))
            .build();

    try (Response response = freshConnectionCallFactory().newCall(request).execute();
        ResponseBody body = response.body()) {
      assertThat(response.code()).isEqualTo(200);
      body.string();
    }

    assertClientSpanHasTimingAttributes();
  }

  @Test
  void timingAttributesOnAsynchronousCallSpan() {
    URI uri = resolveAddress("/success");
    Request request =
        new Request.Builder()
            .url(uri.toString())
            .post(RequestBody.create(MediaType.parse("text/plain"), "hello"))
            .build();

    freshConnectionCallFactory()
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(Call call, IOException e) {}

              @Override
              public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                  body.string();
                }
              }
            });

    assertClientSpanHasTimingAttributes();
  }

  private static void assertClientSpanHasTimingAttributes() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().asMap())
                                    .containsKeys(
                                        CALL_START,
                                        DNS_START,
                                        DNS_END,
                                        CONNECT_START,
                                        CONNECT_END,
                                        REQUEST_HEADERS_START,
                                        REQUEST_HEADERS_END,
                                        RESPONSE_HEADERS_START,
                                        RESPONSE_HEADERS_END,
                                        RESPONSE_BODY_START,
                                        RESPONSE_BODY_END,
                                        CALL_END)),
                span -> span.hasKind(SpanKind.SERVER)));
  }
}
