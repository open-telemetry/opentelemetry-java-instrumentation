/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class McpClientEndToEndTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void tracesComposedAsyncClientInitializationAndToolCall() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpAsyncClient client = McpClient.async(transport).build();

    // Construct both publishers before initialization completes to verify that the negotiated
    // protocol version is resolved when the tool-call publisher is subscribed.
    var initializeAndCallTool =
        client
            .initialize()
            .then(
                client.callTool(
                    new McpSchema.CallToolRequest("async-tool", Map.of("secret", "value"), null)));

    testing.runWithSpan(
        "parent",
        () -> {
          initializeAndCallTool.block();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call async-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(MCP_PROTOCOL_VERSION, ProtocolVersions.MCP_2024_11_05)));
  }

  @Test
  void tracesSyncClientToolCallWithoutDuplicateSpan() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();

    testing.runWithSpan(
        "parent",
        () ->
            client.callTool(
                new McpSchema.CallToolRequest("sync-tool", Map.of("secret", "value"), null)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call sync-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))));
  }
}
