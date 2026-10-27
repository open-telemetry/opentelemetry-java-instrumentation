/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

class Vaadin16Test extends AbstractVaadin14Test {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private int syncId;
  private int clientId;

  @Override
  protected void initializeUi(String sessionCookie) {
    AggregatedHttpRequest initRequest =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(HttpMethod.GET, h1Address.resolve("main?v-r=init").toString())
                .set(HttpHeaderNames.COOKIE, sessionCookie)
                .build());
    AggregatedHttpResponse initResponse =
        client.execute(initRequest).aggregate().join();
    assertThat(initResponse.status().code()).isEqualTo(200);
    JsonNode appConfig = parseJson(initResponse.contentUtf8()).path("appConfig");
    JsonNode initialUidl = appConfig.path("uidl");
    String appId = appConfig.path("appId").asText();
    String csrfToken = initialUidl.path("Vaadin-Security-Key").asText();
    int uiId = appConfig.path("v-uiId").asInt(-1);
    assertThat(appId).isNotEmpty();
    assertThat(csrfToken).isNotEmpty();
    assertThat(uiId).isGreaterThanOrEqualTo(0);

    ObjectNode connectClientRpc = objectMapper.createObjectNode();
    connectClientRpc.put("type", "publishedEventHandler");
    connectClientRpc.put("node", 1);
    connectClientRpc.put("templateEventMethodName", "connectClient");
    connectClientRpc
        .putArray("templateEventMethodArgs")
        .add("flow-container-" + appId.toLowerCase(Locale.ROOT))
        .add(appId)
        .add("main");
    ObjectNode connectClientBody = objectMapper.createObjectNode();
    connectClientBody.put("csrfToken", csrfToken);
    connectClientBody.putArray("rpc").add(connectClientRpc);
    connectClientBody.put("syncId", initialUidl.path("syncId").asInt());
    connectClientBody.put("clientId", initialUidl.path("clientId").asInt());
    AggregatedHttpRequest connectClientRequest =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(
                    HttpMethod.POST,
                    h1Address.resolve("main?v-r=uidl&v-uiId=" + uiId).toString())
                .set(HttpHeaderNames.COOKIE, sessionCookie)
                .contentType(MediaType.JSON_UTF_8)
                .build(),
            HttpData.ofUtf8(connectClientBody.toString()));
    AggregatedHttpResponse connectClientResponse =
        client.execute(connectClientRequest).aggregate().join();
    assertThat(connectClientResponse.status().code()).isEqualTo(200);
    String connectClientContent = connectClientResponse.contentUtf8();
    assertThat(connectClientContent).startsWith("for(;;);");
    JsonNode connectClientUidl =
        parseJson(connectClientContent.substring("for(;;);".length())).path(0);
    syncId = connectClientUidl.path("syncId").asInt();
    clientId = connectClientUidl.path("clientId").asInt();
  }

  private JsonNode parseJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected int getSyncId() {
    return syncId;
  }

  @Override
  protected int getClientId() {
    return clientId;
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
