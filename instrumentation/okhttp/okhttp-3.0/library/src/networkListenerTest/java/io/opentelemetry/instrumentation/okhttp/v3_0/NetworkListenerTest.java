/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
  void synchronousCall() throws Exception {
    URI uri = resolveHttpsAddress("/success");
    Object[] callAndRequest = prepareCallAndRequest(uri);
    Call.Factory callFactory = (Call.Factory) callAndRequest[0];
    okhttp3.Request request = (okhttp3.Request) callAndRequest[1];

    try (Response response = callFactory.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
    }

    SpanData clientSpan = assertSpans(uri, 200, null, true);
    assertLogWithAllNetworkTimingAttributes(clientSpan);
  }

  @Test
  void asynchronousCall() throws Exception {
    URI uri = resolveHttpsAddress("/success");
    Object[] callAndRequest = prepareCallAndRequest(uri);
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

    SpanData clientSpan = assertSpans(uri, 200, null, true);
    assertLogWithAllNetworkTimingAttributes(clientSpan);
  }

  @Test
  void connectionError() {
    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');
    Object[] callAndRequest = prepareCallAndRequest(uri);
    Call.Factory callFactory = (Call.Factory) callAndRequest[0];
    okhttp3.Request request = (okhttp3.Request) callAndRequest[1];

    Throwable thrown = null;
    try {
      callFactory.newCall(request).execute();
    } catch (Exception e) {
      thrown = e;
    }
    assertThat(thrown).isNotNull();

    SpanData clientSpan = assertSpans(uri, null, thrown, false);
    assertLog(
        clientSpan,
        attrMap -> {
          assertThat(attrMap)
              .containsKeys(
                  AttributeKey.longKey("http.call.start_time"),
                  AttributeKey.longKey("http.dns.start_time"),
                  AttributeKey.longKey("http.dns.end_time"),
                  AttributeKey.longKey("http.connect.start_time"),
                  AttributeKey.longKey("http.call.end_time"));
        });
  }

  private Object[] prepareCallAndRequest(URI uri) {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder().connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS));
    Call.Factory callFactory = createCallFactory(builder);
    RequestBody requestBody =
        RequestBody.create(
            MediaType.parse("application/json"), "{\"key\":\"value\",\"data\":\"test payload\"}");
    okhttp3.Request request =
        new okhttp3.Request.Builder().url(uri.toString()).post(requestBody).build();
    return new Object[] {callFactory, request};
  }

  private SpanData assertSpans(
      URI uri, Integer statusCode, Throwable exception, boolean expectServerSpan) {

    SpanData[] spanHolder = new SpanData[1];

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();

          // Client span assertion
          spanAssertions.add(
              span -> {
                SpanDataAssert assertion =
                    assertClientSpan(span, uri, "POST", statusCode, null).hasNoParent();
                if (exception != null) {
                  assertion.hasException(exception);
                }
                spanHolder[0] = span.actual();
              });

          // Server span assertion
          if (expectServerSpan) {
            spanAssertions.add(span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
          }

          // Safe: toArray with generic array creation; type is preserved by
          // List<Consumer<SpanDataAssert>>
          @SuppressWarnings({"unchecked", "rawtypes"})
          Consumer<SpanDataAssert>[] assertionsArray = spanAssertions.toArray(new Consumer[0]);
          trace.hasSpansSatisfyingExactly(assertionsArray);
        });

    SpanData clientSpan = spanHolder[0];
    assertThat(clientSpan)
        .withFailMessage("Http client span was not found in the trace")
        .isNotNull();

    return clientSpan;
  }

  private static void assertLogWithAllNetworkTimingAttributes(SpanData clientSpan) {
    assertLog(
        clientSpan,
        attrMap -> {
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
  }

  private static void assertLog(
      SpanData clientSpan, Consumer<Map<AttributeKey<?>, Object>> attributeAssertion) {

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("http.client.network_timing");
          logRecord.hasSpanContext(clientSpan.getSpanContext());

          logRecord.hasAttributesSatisfying(
              attrs -> {
                Map<AttributeKey<?>, Object> attrMap = attrs.asMap();
                attributeAssertion.accept(attrMap);
              });
        });
  }
}
