/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class McpHttpCustomizerContextTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<JsonNode> toolMessage = new AtomicReference<>();
  private final AtomicReference<String> toolHttpTraceparent = new AtomicReference<>();
  private final AtomicReference<String> customHeader = new AtomicReference<>();
  private HttpClientStreamableHttpTransport.Builder transportBuilder;
  private HttpClient tokenClient;
  private URI tokenUri;

  @BeforeEach
  void setUp() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    cleanup.deferCleanup(() -> server.stop(0));
    server.createContext("/mcp", this::handleMcpRequest);
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
    tokenUri = URI.create(baseUri + "/token");
    tokenClient = HttpClient.newHttpClient();
    transportBuilder =
        HttpClientStreamableHttpTransport.builder(baseUri)
            .endpoint("/mcp")
            .openConnectionOnStartup(false);
  }

  @Test
  void restoresRootContextForSyncCustomizer() {
    AtomicReference<Context> customizerContext = new AtomicReference<>();
    transportBuilder.httpRequestCustomizer((builder, method, endpoint, body, transportContext) -> {
      if (body != null && body.contains(McpSchema.METHOD_TOOLS_CALL)) {
        customizerContext.set(Context.current());
        HttpResponse<String> response =
            tokenClient
                .sendAsync(
                    HttpRequest.newBuilder(tokenUri).build(), HttpResponse.BodyHandlers.ofString())
                .join();
        builder.header("X-Customizer", response.body());
      }
    });
    McpAsyncClient client = newClient();
    assertThat(client.callTool(new McpSchema.CallToolRequest("test", Map.of(), null)).block())
        .isNotNull();
    assertThat(Span.fromContext(customizerContext.get()).getSpanContext().isValid()).isFalse();
    assertThat(customHeader.get()).isEqualTo("token");
    testing.waitAndAssertTraces(
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
                    span.hasKind(CLIENT)
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
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void restoresRootContextForAsyncCustomizerAndItsPublisher() {
    Queue<Context> contexts = new ConcurrentLinkedQueue<>();
    configureAsyncCustomizer(contexts);
    McpAsyncClient client = newClient();
    assertThat(client.callTool(new McpSchema.CallToolRequest("test", Map.of(), null)).block())
        .isNotNull();
    assertThat(contexts)
        .hasSize(3)
        .allSatisfy(
            context -> assertThat(Span.fromContext(context).getSpanContext().isValid()).isFalse());
    assertThat(customHeader.get()).isEqualTo("token");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call test").hasKind(CLIENT).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void restoresCallerForAsyncCustomizerAndItsPublisher() {
    ContextKey<String> key = ContextKey.named("customizer-caller");
    Queue<Context> contexts = new ConcurrentLinkedQueue<>();
    configureAsyncCustomizer(contexts);
    McpAsyncClient client = newClient();
    AtomicReference<Context> caller = new AtomicReference<>();
    testing.runWithSpan(
        "parent",
        () -> {
          caller.set(
              Baggage.builder()
                  .put("tenant", "caller")
                  .build()
                  .storeInContext(Context.current().with(key, "caller")));
          assertThat(
                  ContextPropagationOperator.runWithContext(
                          client.callTool(new McpSchema.CallToolRequest("test", Map.of(), null)),
                          caller.get())
                      .block())
              .isNotNull();
        });
    assertThat(contexts)
        .hasSize(3)
        .allSatisfy(context -> {
          assertThat(context.get(key)).isEqualTo("caller");
          assertThat(Baggage.fromContext(context).getEntryValue("tenant")).isEqualTo("caller");
          assertThat(Span.fromContext(context).getSpanContext())
              .isEqualTo(Span.fromContext(caller.get()).getSpanContext());
        });
    assertThat(customHeader.get()).isEqualTo("token");
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span -> span.hasName("tools/call test").hasKind(CLIENT).hasParent(trace.getSpan(0)),
            span -> span.hasKind(CLIENT).hasParent(trace.getSpan(1)),
            span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void preservesExplicitCustomizerPublisherContext() {
    AtomicReference<Context> explicitContext = new AtomicReference<>();
    AtomicReference<Context> invocationContext = new AtomicReference<>();
    AtomicReference<Context> publisherContext = new AtomicReference<>();
    transportBuilder.asyncHttpRequestCustomizer(
        (builder, method, endpoint, body, transportContext) -> {
          if (body == null || !body.contains(McpSchema.METHOD_TOOLS_CALL)) {
            return Mono.just(builder);
          }
          invocationContext.set(Context.current());
          return Mono.deferContextual(
                  reactorContext -> {
                    Context context =
                        ContextPropagationOperator.getOpenTelemetryContext(
                            reactor.util.context.Context.of(reactorContext), Context.root());
                    try (Scope ignored = context.makeCurrent()) {
                      publisherContext.set(Context.current());
                      return Mono.fromFuture(
                              tokenClient.sendAsync(
                                  HttpRequest.newBuilder(tokenUri).build(),
                                  HttpResponse.BodyHandlers.ofString()))
                          .map(response -> builder.header("X-Customizer", response.body()));
                    }
                  })
              .subscribeOn(Schedulers.boundedElastic())
              .contextWrite(context ->
                  ContextPropagationOperator.storeOpenTelemetryContext(
                      context, explicitContext.get()));
        });
    McpAsyncClient client = newClient();
    testing.runWithSpan("explicit", () -> explicitContext.set(Context.current()));
    assertThat(client.callTool(new McpSchema.CallToolRequest("test", Map.of(), null)).block())
        .isNotNull();
    assertThat(Span.fromContext(invocationContext.get()).getSpanContext().isValid()).isFalse();
    assertThat(Span.fromContext(publisherContext.get()).getSpanContext())
        .isEqualTo(Span.fromContext(explicitContext.get()).getSpanContext());
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("explicit").hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call test").hasKind(CLIENT).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  private McpAsyncClient newClient() {
    McpAsyncClient client = McpClient.async(transportBuilder.build()).build();
    cleanup.deferCleanup(client::close);
    client.initialize().block();
    testing.waitForTraces(3);
    testing.clearData();
    return client;
  }

  private void configureAsyncCustomizer(Queue<Context> contexts) {
    transportBuilder.asyncHttpRequestCustomizer(
        (builder, method, endpoint, body, transportContext) -> {
          if (body == null || !body.contains(McpSchema.METHOD_TOOLS_CALL)) {
            return Mono.just(builder);
          }
          contexts.add(Context.current());
          return Mono.defer(
                  () -> {
                    contexts.add(Context.current());
                    return Mono.fromFuture(
                        tokenClient.sendAsync(
                            HttpRequest.newBuilder(tokenUri).build(),
                            HttpResponse.BodyHandlers.ofString()));
                  })
              .doOnNext(unused -> contexts.add(Context.current()))
              .map(response -> builder.header("X-Customizer", response.body()))
              .subscribeOn(Schedulers.boundedElastic());
        });
  }

  private void handleMcpRequest(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equals("POST")) {
      exchange.sendResponseHeaders(405, -1);
      exchange.close();
      return;
    }
    JsonNode request = mapper.readTree(exchange.getRequestBody());
    if (!request.has("id")) {
      exchange.sendResponseHeaders(202, -1);
      exchange.close();
      return;
    }
    Map<String, Object> result;
    if (request.path("method").asText().equals(McpSchema.METHOD_INITIALIZE)) {
      result =
          Map.of(
              "protocolVersion",
              "2025-03-26",
              "capabilities",
              Map.of("tools", Map.of("listChanged", false)),
              "serverInfo",
              Map.of("name", "test", "version", "1.0"));
    } else {
      toolMessage.set(request);
      toolHttpTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
      customHeader.set(exchange.getRequestHeaders().getFirst("X-Customizer"));
      result = Map.of("content", List.of(Map.of("type", "text", "text", "result")));
    }
    byte[] response =
        mapper.writeValueAsBytes(
            Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }
}
