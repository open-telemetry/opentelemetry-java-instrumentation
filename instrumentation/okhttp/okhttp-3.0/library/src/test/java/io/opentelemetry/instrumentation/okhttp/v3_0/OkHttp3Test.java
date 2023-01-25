/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OkHttp3Test extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    return OkHttpTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .newCallFactory(clientBuilder.build());
  }

  @Test
  void wrapperSpan() throws Exception {
    Call.Factory callFactory =
        OkHttpTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
            .setEmitEncompassingSpan(true)
            .build()
            .newCallFactory(getClientBuilder(false).build());

    URI uri = resolveAddress("/success");
    Request request = buildRequest("GET", uri, emptyMap());
    Response response = callFactory.newCall(request).execute();
    try (ResponseBody ignored = response.body()) {
      assertThat(response.code()).isEqualTo(200);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("okhttp3.Call.execute()").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString()),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))));
  }

  @Test
  void connectionErrorSpan() {
    Call.Factory callFactory =
        OkHttpTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
            .setCreateSpanForConnectionErrors(true)
            .build()
            .newCallFactory(getClientBuilder(false).build());

    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT + '/');

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      Request request = buildRequest("GET", uri, emptyMap());
                      return callFactory.newCall(request).execute();
                    }));

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("parent")
                      .hasKind(SpanKind.INTERNAL)
                      .hasNoParent()
                      .hasStatus(StatusData.error())
                      .hasException(thrown),
              span ->
                  span.hasName("GET")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.error())
                      .hasException(thrown)
                      .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString()));
        });
  }
}
