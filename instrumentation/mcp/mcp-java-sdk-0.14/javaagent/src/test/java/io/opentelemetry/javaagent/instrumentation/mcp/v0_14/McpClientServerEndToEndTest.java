/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;

class McpClientServerEndToEndTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void connectsClientAndServerToolCallSpans() {
    TestMcpClientServerTransport transport = new TestMcpClientServerTransport();
    McpRequestHandler<McpSchema.CallToolResult> toolHandler =
        (exchange, params) ->
            Mono.just(McpSchema.CallToolResult.builder().addTextContent("secret result").build());
    McpServerSession serverSession =
        new McpServerSession(
            "e2e-session",
            Duration.ofSeconds(1),
            transport,
            request ->
                Mono.just(
                    new McpSchema.InitializeResult(
                        ProtocolVersions.MCP_2024_11_05,
                        McpSchema.ServerCapabilities.builder().tools(false).build(),
                        new McpSchema.Implementation("test-server", "1.0"),
                        null)),
            Map.of(McpSchema.METHOD_TOOLS_CALL, toolHandler),
            Map.of());
    transport.setServerSession(serverSession);
    McpAsyncClient client = McpClient.async(transport).build();
    client.initialize().block();

    testing.runWithSpan(
        "agent",
        () ->
            client
                .callTool(
                    new McpSchema.CallToolRequest(
                        "delete-file", Map.of("path", "/sensitive/file"), null))
                .block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("agent").hasNoParent(),
                span ->
                    span.hasName("tools/call delete-file")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("tools/call delete-file")
                        .hasKind(SERVER)
                        .hasParent(trace.getSpan(1))));
  }
}
