/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class McpSseClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<HttpExchange> sse = new AtomicReference<>();
  private final AtomicReference<JsonNode> toolMessage = new AtomicReference<>();
  private final AtomicReference<String> toolHttpTraceparent = new AtomicReference<>();
  private final AtomicReference<String> customHeader = new AtomicReference<>();

  @Test
  void propagatesToolContextAndRestoresCallerAcrossSseAndPost() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    cleanup.deferCleanup(() -> server.stop(0));
    server.createContext(
        "/sse",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          sse.set(exchange);
          exchange.getResponseBody().write("event: endpoint\ndata: /messages\n\n".getBytes(UTF_8));
          exchange.getResponseBody().flush();
        });
    server.createContext("/messages", this::handleMessage);
    server.createContext(
        "/token",
        exchange -> {
          byte[] response = "token".getBytes(UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    String baseUri = "http://localhost:" + server.getAddress().getPort();
    URI tokenUri = URI.create(baseUri + "/token");
    HttpClient tokenClient = HttpClient.newHttpClient();
    Queue<Context> customizerContexts = new ConcurrentLinkedQueue<>();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    McpAsyncClient client =
        McpClient.async(
                HttpClientSseClientTransport.builder(baseUri)
                    .asyncHttpRequestCustomizer(
                        (builder, method, endpoint, body, transportContext) -> {
                          if (body == null || !body.contains(McpSchema.METHOD_TOOLS_CALL)) {
                            return Mono.just(builder);
                          }
                          customizerContexts.add(Context.current());
                          return Mono.defer(
                                  () -> {
                                    customizerContexts.add(Context.current());
                                    return Mono.fromFuture(
                                        tokenClient.sendAsync(
                                            HttpRequest.newBuilder(tokenUri).build(),
                                            HttpResponse.BodyHandlers.ofString()));
                                  })
                              .doOnNext(unused -> customizerContexts.add(Context.current()))
                              .map(response -> builder.header("X-Customizer", response.body()))
                              .subscribeOn(Schedulers.boundedElastic());
                        })
                    .build())
            .build();
    try {
      client.initialize().block();
      CompletableFuture<McpSchema.CallToolResult> response =
          client
              .callTool(new McpSchema.CallToolRequest("test", Map.of(), null))
              .doOnNext(result -> {
                callbackContext.set(Context.current());
                testing.runWithSpan("callback", () -> {});
              })
              .toFuture();
      await()
          .untilAsserted(() ->
              assertThat(testing.spans())
                  .anySatisfy(span -> {
                    assertThat(span.getName()).isEqualTo("POST");
                    assertThat(span.getParentSpanContext().isValid()).isTrue();
                  }));
      assertThat(response).isNotDone();
      assertThat(testing.spans()).extracting(SpanData::getName).doesNotContain("tools/call test");
      sendResponse(toolMessage.get(), Map.of("content", List.of(), "isError", false));
      assertThat(response.get(10, SECONDS)).isNotNull();
      assertThat(customHeader.get()).isEqualTo("token");
      assertThat(customizerContexts)
          .hasSize(3)
          .allSatisfy(context ->
              assertThat(Span.fromContext(context).getSpanContext().isValid()).isFalse());
      assertThat(Span.fromContext(callbackContext.get()).getSpanContext().isValid()).isFalse();
    } finally {
      client.close();
      if (sse.get() != null) {
        sse.get().close();
      }
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("tools/call test")
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .satisfies(spanData ->
                            assertThat(
                                    toolMessage
                                        .get()
                                        .path("params")
                                        .path("_meta")
                                        .path("traceparent")
                                        .asText())
                                .isEqualTo(
                                    "00-"
                                        + spanData.getTraceId()
                                        + "-"
                                        + spanData.getSpanId()
                                        + "-"
                                        + spanData.getSpanContext().getTraceFlags().asHex())),
                span ->
                    span.hasName("POST")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(spanData ->
                            assertThat(toolHttpTraceparent.get())
                                .isEqualTo(
                                    "00-"
                                        + spanData.getTraceId()
                                        + "-"
                                        + spanData.getSpanId()
                                        + "-"
                                        + spanData.getSpanContext().getTraceFlags().asHex()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("callback").hasNoParent()));
  }

  private void handleMessage(HttpExchange exchange) throws IOException {
    JsonNode request = mapper.readTree(exchange.getRequestBody());
    if (request.path("method").asText().equals(McpSchema.METHOD_TOOLS_CALL)) {
      toolMessage.set(request);
      toolHttpTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
      customHeader.set(exchange.getRequestHeaders().getFirst("X-Customizer"));
    }
    exchange.sendResponseHeaders(202, -1);
    if (request.path("method").asText().equals(McpSchema.METHOD_INITIALIZE)) {
      sendResponse(
          request,
          Map.of(
              "protocolVersion",
              "2024-11-05",
              "capabilities",
              Map.of("tools", Map.of("listChanged", false)),
              "serverInfo",
              Map.of("name", "test", "version", "1.0")));
    }
    exchange.close();
  }

  private void sendResponse(JsonNode request, Map<String, Object> result) throws IOException {
    String response =
        mapper.writeValueAsString(
            Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
    sse.get()
        .getResponseBody()
        .write(("event: message\ndata: " + response + "\n\n").getBytes(UTF_8));
    sse.get().getResponseBody().flush();
  }
}
