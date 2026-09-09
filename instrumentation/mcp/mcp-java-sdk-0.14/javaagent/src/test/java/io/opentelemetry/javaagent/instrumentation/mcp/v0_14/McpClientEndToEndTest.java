/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class McpClientEndToEndTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void tracesAsyncClientToolCall() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpAsyncClient client = McpClient.async(transport).build();
    client.initialize().block();

    testing.runWithSpan(
        "parent",
        () ->
            client
                .callTool(
                    new McpSchema.CallToolRequest("async-tool", Map.of("secret", "value"), null))
                .block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call async-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))));
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
