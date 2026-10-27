/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.util.List;

class Vaadin16Test extends AbstractVaadin14Test {

  @Override
  protected void initializeUi(String sessionCookie) {
    AggregatedHttpRequest initRequest =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(HttpMethod.POST, h1Address.resolve("main?v-r=init").toString())
                .set(HttpHeaderNames.COOKIE, sessionCookie)
                .build());
    AggregatedHttpResponse initResponse = client.execute(initRequest).aggregate().join();
    assertThat(initResponse.status().code()).isEqualTo(200);
  }

  @Override
  void assertFirstRequest() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              if (!controllerTelemetryEnabled()) {
                for (List<SpanData> trace : traces) {
                  assertThat(trace).anySatisfy(span -> assertThat(span).hasKind(SpanKind.SERVER));
                  assertNoControllerSpans(trace);
                }
                return;
              }
              TracesAssert.assertThat(traces)
                  .satisfies(
                      trace -> {
                        assertThat(trace.get(0))
                            .satisfies(
                                spans ->
                                    assertThat(spans.get(0))
                                        .hasName("GET IndexHtmlRequestHandler.handleRequest")
                                        .hasNoParent()
                                        .hasKind(SpanKind.SERVER));
                        assertThat(trace)
                            .anySatisfy(
                                spans -> {
                                  assertThat(spans.get(0))
                                      .hasName("POST " + getContextPath() + "/main")
                                      .hasNoParent()
                                      .hasKind(SpanKind.SERVER);
                                  assertThat(spans.get(1))
                                      .hasName("SpringVaadinServletService.handleRequest")
                                      .hasParent(spans.get(0))
                                      .hasKind(SpanKind.INTERNAL);
                                  // we don't assert all the handler spans as these vary between
                                  // vaadin versions
                                  assertThat(spans.get(spans.size() - 3))
                                      .hasName("UidlRequestHandler.handleRequest")
                                      .hasParent(spans.get(1))
                                      .hasKind(SpanKind.INTERNAL);
                                  assertThat(spans.get(spans.size() - 2))
                                      .hasName("PublishedServerEventHandlerRpcHandler.handle")
                                      .hasParent(spans.get(spans.size() - 3))
                                      .hasKind(SpanKind.INTERNAL);
                                  assertThat(spans.get(spans.size() - 1))
                                      .hasName("JavaScriptBootstrapUI.connectClient")
                                      .hasParent(spans.get(spans.size() - 2))
                                      .hasKind(SpanKind.INTERNAL);
                                });
                      });
            });
  }
}
