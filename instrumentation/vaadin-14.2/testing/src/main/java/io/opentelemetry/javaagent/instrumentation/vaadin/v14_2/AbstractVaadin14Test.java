/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.util.List;
import org.junit.jupiter.api.Test;
import test.vaadin.MainView;

public abstract class AbstractVaadin14Test extends AbstractVaadinTest {

  @Test
  void processInitialRouteAndClientEvent() {
    AggregatedHttpResponse initialResponse =
        client.get(h1Address.resolve("main").toString()).aggregate().join();
    assertThat(initialResponse.status().code()).isEqualTo(200);
    String setCookie = initialResponse.headers().get(HttpHeaderNames.SET_COOKIE);
    String sessionCookie = setCookie.substring(0, setCookie.indexOf(';'));
    initializeUi(sessionCookie);
    assertThat(MainView.getButtonNodeId()).isGreaterThanOrEqualTo(0);
    assertThat(MainView.getUiId()).isGreaterThanOrEqualTo(0);
    assertThat(MainView.getCsrfToken()).isNotEmpty();
    assertFirstRequest();

    testing.clearData();
    String body =
        "{\"csrfToken\":\""
            + MainView.getCsrfToken()
            + "\",\"rpc\":[{\"type\":\"event\",\"node\":"
            + MainView.getButtonNodeId()
            + ",\"event\":\"click\"}],\"syncId\":"
            + getSyncId()
            + ",\"clientId\":"
            + getClientId()
            + "}";
    AggregatedHttpRequest uidlRequest =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(
                    HttpMethod.POST,
                    h1Address.resolve("main?v-r=uidl&v-uiId=" + MainView.getUiId()).toString())
                .set(HttpHeaderNames.COOKIE, sessionCookie)
                .contentType(MediaType.JSON_UTF_8)
                .build(),
            HttpData.ofUtf8(body));
    AggregatedHttpResponse eventResponse = client.execute(uidlRequest).aggregate().join();
    assertThat(eventResponse.status().code()).isEqualTo(200);
    assertThat(eventResponse.contentUtf8()).contains("Other view");
    assertClientEvent();
  }

  protected void initializeUi(String sessionCookie) {}

  protected int getSyncId() {
    return 0;
  }

  protected int getClientId() {
    return 0;
  }

  @Override
  void assertFirstRequest() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              assertThat(traces.get(0))
                  .satisfies(
                      spans -> {
                        assertThat(spans.get(0))
                            .hasName("GET " + getContextPath() + "/main")
                            .hasNoParent()
                            .hasKind(SpanKind.SERVER);
                        if (controllerTelemetryEnabled()) {
                          assertThat(spans.get(1))
                              .hasName("SpringVaadinServletService.handleRequest")
                              .hasParent(spans.get(0))
                              .hasKind(SpanKind.INTERNAL);
                          // we don't assert all the handler spans as these vary between
                          // vaadin versions
                          assertThat(spans.get(spans.size() - 1))
                              .hasName("BootstrapHandler.handleRequest")
                              .hasParent(spans.get(1))
                              .hasKind(SpanKind.INTERNAL);
                        } else {
                          assertNoControllerSpans(spans);
                        }
                      });
            });
  }

  private void assertClientEvent() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              List<SpanData> spans = traces.get(0);
              assertThat(spans.get(0))
                  .hasName("POST " + getContextPath() + "/main")
                  .hasNoParent()
                  .hasKind(SpanKind.SERVER);
              if (controllerTelemetryEnabled()) {
                assertThat(spans.get(1))
                    .hasName("SpringVaadinServletService.handleRequest")
                    .hasParent(spans.get(0))
                    .hasKind(SpanKind.INTERNAL);
                assertThat(spans.get(spans.size() - 2))
                    .hasName("UidlRequestHandler.handleRequest")
                    .hasParent(spans.get(1))
                    .hasKind(SpanKind.INTERNAL);
                assertThat(spans.get(spans.size() - 1))
                    .hasName("EventRpcHandler.handle/click")
                    .hasParent(spans.get(spans.size() - 2))
                    .hasKind(SpanKind.INTERNAL)
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            CODE_FUNCTION_NAME,
                            "com.vaadin.flow.server.communication.rpc.EventRpcHandler.handle"));
              } else {
                assertNoControllerSpans(spans);
              }
            });
  }
}
