/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_SESSION_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class McpServerRequestInstrumentationTest {
  private static final McpSchema.ClientCapabilities CLIENT_CAPABILITIES =
      McpSchema.ClientCapabilities.builder().build();
  private static final McpSchema.Implementation CLIENT_INFO =
      new McpSchema.Implementation("test-client", "1.0");
  private static final McpSchema.InitializeResult INITIALIZE_RESULT =
      new McpSchema.InitializeResult(
          ProtocolVersions.MCP_2024_11_05,
          McpSchema.ServerCapabilities.builder().tools(false).build(),
          new McpSchema.Implementation("test-server", "1.0"),
          null);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void tracesStatefulToolCallWithRemoteParentAndTransportLinkWithoutPayloads() {
    TestMcpServerTransport transport = new TestMcpServerTransport();
    McpServerSession session =
        newStatefulSession(
            transport,
            (exchange, params) ->
                Mono.just(
                    McpSchema.CallToolResult.builder().addTextContent("sensitive result").build()));

    AtomicReference<SpanContext> remoteParent = new AtomicReference<>();
    testing.runWithSpan("remote-parent", () -> remoteParent.set(Span.current().getSpanContext()));
    String traceparent =
        "00-" + remoteParent.get().getTraceId() + "-" + remoteParent.get().getSpanId() + "-01";
    McpSchema.JSONRPCRequest request =
        new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            7,
            Map.of(
                "name",
                "delete-file",
                "arguments",
                Map.of("path", "/sensitive/file"),
                "_meta",
                Map.of("traceparent", traceparent)));
    AtomicReference<SpanContext> transportSpan = new AtomicReference<>();

    testing.runWithSpan(
        "transport",
        () -> {
          transportSpan.set(Span.current().getSpanContext());
          session.handle(request).block();
        });

    assertThat(transport.getResponse().result()).isInstanceOf(McpSchema.CallToolResult.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("remote-parent").hasNoParent(),
                span ->
                    span.hasName("tools/call delete-file")
                        .hasKind(SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())
                        .hasLinksSatisfying(
                            links ->
                                assertThat(links)
                                    .singleElement()
                                    .satisfies(
                                        link ->
                                            assertThat(link.getSpanContext())
                                                .extracting(
                                                    SpanContext::getTraceId, SpanContext::getSpanId)
                                                .containsExactly(
                                                    transportSpan.get().getTraceId(),
                                                    transportSpan.get().getSpanId())))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "delete-file"),
                            equalTo(JSONRPC_REQUEST_ID, "7"),
                            equalTo(MCP_SESSION_ID, "stateful-session"))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("transport").hasNoParent()));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void tracesStreamableToolResultError() {
    TestMcpServerTransport.Streamable transport = new TestMcpServerTransport.Streamable();
    McpStreamableServerSession session =
        new McpStreamableServerSession(
            "streamable-session",
            CLIENT_CAPABILITIES,
            CLIENT_INFO,
            Duration.ofSeconds(1),
            toolHandlers(
                (exchange, params) ->
                    Mono.just(McpSchema.CallToolResult.builder().isError(true).build())),
            Map.of());

    session
        .responseStream(toolCallRequest("failing-tool", "stream-1", Map.of()), transport)
        .block();

    assertThat(transport.getResponse().result()).isInstanceOf(McpSchema.CallToolResult.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("tools/call failing-tool")
                        .hasKind(SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "failing-tool"),
                            equalTo(JSONRPC_REQUEST_ID, "stream-1"),
                            equalTo(MCP_SESSION_ID, "streamable-session"),
                            equalTo(ERROR_TYPE, "tool_error"))));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void tracesStatelessHandlerFailure() {
    TestMcpServerTransport.Stateless transport = new TestMcpServerTransport.Stateless();
    McpStatelessAsyncServer server =
        McpServer.async(transport)
            .toolCall(
                tool("lookup"),
                (context, request) ->
                    Mono.error(
                        new McpError(
                            new McpSchema.JSONRPCResponse.JSONRPCError(
                                -32603, "Internal error", null))))
            .build();

    McpSchema.JSONRPCResponse response =
        transport
            .getHandler()
            .handleRequest(
                McpTransportContext.EMPTY, toolCallRequest("lookup", "stateless-1", Map.of()))
            .block();
    server.close();

    assertThat(response).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("tools/call lookup").hasKind(SERVER).hasNoParent();
                  if (response.error() != null) {
                    assertThat(response.error().code()).isEqualTo(-32603);
                    span.hasStatus(StatusData.create(StatusCode.ERROR, "Internal error"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "lookup"),
                            equalTo(JSONRPC_REQUEST_ID, "stateless-1"),
                            equalTo(RPC_RESPONSE_STATUS_CODE, "-32603"),
                            equalTo(ERROR_TYPE, "-32603"));
                  } else {
                    assertThat(response.result())
                        .isInstanceOfSatisfying(
                            McpSchema.CallToolResult.class,
                            result -> assertThat(result.isError()).isTrue());
                    span.hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "lookup"),
                            equalTo(JSONRPC_REQUEST_ID, "stateless-1"),
                            equalTo(ERROR_TYPE, "tool_error"));
                  }
                }));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void doesNotMarkCallerJsonRpcErrorAsServerFailure() {
    TestMcpServerTransport.Streamable transport = new TestMcpServerTransport.Streamable();
    McpStreamableServerSession session =
        new McpStreamableServerSession(
            "streamable-session",
            CLIENT_CAPABILITIES,
            CLIENT_INFO,
            Duration.ofSeconds(1),
            toolHandlers(
                (exchange, params) ->
                    Mono.error(
                        new McpError(
                            new McpSchema.JSONRPCResponse.JSONRPCError(
                                -32602, "Invalid params", null)))),
            Map.of());

    session
        .responseStream(toolCallRequest("missing-tool", "stream-2", Map.of()), transport)
        .block();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("tools/call missing-tool")
                        .hasKind(SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "missing-tool"),
                            equalTo(JSONRPC_REQUEST_ID, "stream-2"),
                            equalTo(MCP_SESSION_ID, "streamable-session"),
                            equalTo(RPC_RESPONSE_STATUS_CODE, "-32602"))));
  }

  @Test
  void endsServerSpanOnCancellation() {
    TestMcpServerTransport transport = new TestMcpServerTransport();
    McpServerSession session = newStatefulSession(transport, (exchange, params) -> Mono.never());

    Disposable subscription =
        session.handle(toolCallRequest("slow-tool", "request-2", Map.of())).subscribe();
    subscription.dispose();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call slow-tool").hasKind(SERVER).hasNoParent()));
  }

  @Test
  void doesNotTraceOtherMcpMethods() {
    TestMcpServerTransport transport = new TestMcpServerTransport();
    McpServerSession session =
        newStatefulSession(
            transport, (exchange, params) -> Mono.just(McpSchema.CallToolResult.builder().build()));

    session
        .handle(new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "ping", "ping-1", null))
        .block();

    assertThat(testing.spans()).isEmpty();
  }

  private static McpServerSession newStatefulSession(
      TestMcpServerTransport transport, McpRequestHandler<McpSchema.CallToolResult> toolHandler) {
    McpServerSession session =
        new McpServerSession(
            "stateful-session",
            Duration.ofSeconds(1),
            transport,
            request -> Mono.just(INITIALIZE_RESULT),
            toolHandlers(toolHandler),
            Map.of());
    session
        .handle(
            new McpSchema.JSONRPCRequest(
                McpSchema.JSONRPC_VERSION,
                McpSchema.METHOD_INITIALIZE,
                "initialize-1",
                new McpSchema.InitializeRequest(
                    ProtocolVersions.MCP_2024_11_05, CLIENT_CAPABILITIES, CLIENT_INFO)))
        .block();
    session
        .handle(
            new McpSchema.JSONRPCNotification(
                McpSchema.JSONRPC_VERSION, McpSchema.METHOD_NOTIFICATION_INITIALIZED, null))
        .block();
    transport.clear();
    return session;
  }

  private static Map<String, McpRequestHandler<?>> toolHandlers(
      McpRequestHandler<McpSchema.CallToolResult> toolHandler) {
    return Map.of(McpSchema.METHOD_TOOLS_CALL, toolHandler);
  }

  private static McpSchema.JSONRPCRequest toolCallRequest(
      String toolName, String requestId, Map<String, Object> meta) {
    return new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_TOOLS_CALL,
        requestId,
        new McpSchema.CallToolRequest(toolName, Map.of("secret", "value"), meta));
  }

  private static McpSchema.Tool tool(String name) {
    return McpSchema.Tool.builder()
        .name(name)
        .description("test tool")
        .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
        .build();
  }
}
