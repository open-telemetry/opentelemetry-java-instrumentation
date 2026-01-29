/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NetworkListenerTest extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    clientBuilder.protocols(singletonList(Protocol.HTTP_1_1));
    return OkHttpTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .newCallFactoryWithNetworkTiming(clientBuilder.build());
  }

  @Test
  void networkTimingClient() throws Exception {
    Object[] callAndRequest = prepareCallAndRequest();
    Call.Factory callFactory = (Call.Factory) callAndRequest[0];
    okhttp3.Request request = (okhttp3.Request) callAndRequest[1];

    try (Response response = callFactory.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
    }

    assertAllSignalsAndTimingAttributes();
  }

  @Test
  void networkTimingClient_asyncCallback() throws Exception {
    Object[] callAndRequest = prepareCallAndRequest();
    Call.Factory callFactory = (Call.Factory) callAndRequest[0];
    okhttp3.Request request = (okhttp3.Request) callAndRequest[1];

    AtomicInteger responseCode = new AtomicInteger();

    callFactory
        .newCall(request)
        .enqueue(
            new okhttp3.Callback() {
              @Override
              public void onFailure(okhttp3.Call call, java.io.IOException e) {
                responseCode.set(-1);
              }

              @Override
              public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                try {
                  responseCode.set(response.code());
                } finally {
                  response.close();
                }
              }
            });

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(responseCode.get()).isNotZero());
    assertThat(responseCode.get()).isEqualTo(200);

    assertAllSignalsAndTimingAttributes();
  }

  private Object[] prepareCallAndRequest() {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder().connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS));
    Call.Factory callFactory = createCallFactory(builder);
    RequestBody requestBody =
        RequestBody.create(
            MediaType.parse("application/json"), "{\"key\":\"value\",\"data\":\"test payload\"}");
    okhttp3.Request request =
        new okhttp3.Request.Builder()
            .url(resolveHttpsAddress("/success").toString())
            .post(requestBody)
            .build();
    return new Object[] {callFactory, request};
  }

  private void assertAllSignalsAndTimingAttributes() {
    SpanData[] clientSpanHolder = new SpanData[1];
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  assertClientSpan(span, resolveHttpsAddress("/success"), "POST", 200, null)
                      .hasNoParent();
                  clientSpanHolder[0] = span.actual();
                },
                span -> assertServerSpan(span).hasParent(trace.getSpan(0))));

    SpanData clientSpan = clientSpanHolder[0];
    assertThat(clientSpan)
        .withFailMessage("Http client span was not found in the trace")
        .isNotNull();

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("http.client.network_timing");

          // Assert span id and trace id match client span using hasSpanContext()
          logRecord.hasSpanContext(clientSpan.getSpanContext());

          logRecord.hasAttributesSatisfying(
              attrs -> {
                Map<AttributeKey<?>, Object> attrMap = attrs.asMap();
                assertThat(attrMap)
                    .containsKeys(
                        AttributeKey.longKey("http.call.start_time"),
                        AttributeKey.longKey("http.call.end_time"),
                        AttributeKey.longKey("http.dns.start_time"),
                        AttributeKey.longKey("http.dns.end_time"),
                        AttributeKey.longKey("http.connect.start_time"),
                        AttributeKey.longKey("http.connect.end_time"),
                        AttributeKey.longKey("http.secure_connect.start_time"),
                        AttributeKey.longKey("http.secure_connect.end_time"),
                        AttributeKey.longKey("http.request.headers.start_time"),
                        AttributeKey.longKey("http.request.headers.end_time"),
                        AttributeKey.longKey("http.request.body.start_time"),
                        AttributeKey.longKey("http.request.body.end_time"),
                        AttributeKey.longKey("http.response.headers.start_time"),
                        AttributeKey.longKey("http.response.headers.end_time"),
                        AttributeKey.longKey("http.response.body.start_time"),
                        AttributeKey.longKey("http.response.body.end_time"));
              });
        });
  }
}
