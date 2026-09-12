/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class McpClientSessionInstrumentationTest {
  private static final TypeRef<McpSchema.CallToolResult> CALL_TOOL_RESULT_TYPE =
      new TypeRef<McpSchema.CallToolResult>() {};

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void tracesToolCallAndInjectsContextWithoutCapturingPayloads() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);
    McpSchema.CallToolRequest request =
        new McpSchema.CallToolRequest(
            "delete-file", Map.of("path", "/sensitive/file"), Map.of("tenant", "test"));

    McpSchema.CallToolResult result =
        testing.runWithSpan(
            "parent",
            () ->
                session
                    .sendRequest(McpSchema.METHOD_TOOLS_CALL, request, CALL_TOOL_RESULT_TYPE)
                    .block());

    assertThat(result).isNotNull();
    McpSchema.CallToolRequest sentRequest =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(sentRequest.arguments()).containsEntry("path", "/sensitive/file");
    assertThat(sentRequest.meta()).containsEntry("tenant", "test").containsKey("traceparent");
    assertThat(sentRequest.meta().get("traceparent").toString())
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call delete-file")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "delete-file"),
                            equalTo(
                                JSONRPC_REQUEST_ID, transport.getToolRequest().id().toString()))));
  }

  @Test
  void usesReactorContextWhenSubscriptionOccursAfterParentScopeCloses() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);

    Mono<McpSchema.CallToolResult> publisher =
        testing.runWithSpan(
            "parent",
            () ->
                ContextPropagationOperator.runWithContext(
                    session.sendRequest(
                        McpSchema.METHOD_TOOLS_CALL,
                        new McpSchema.CallToolRequest("async-tool", Map.of(), null),
                        CALL_TOOL_RESULT_TYPE),
                    Context.current()));
    assertThat(publisher.block()).isNotNull();

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
  @SuppressWarnings("deprecation") // using deprecated semconv
  void recordsJsonRpcError() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.JSON_RPC_ERROR);
    McpClientSession session = newSession(transport);

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        session
                            .sendRequest(
                                McpSchema.METHOD_TOOLS_CALL,
                                new McpSchema.CallToolRequest("missing-tool", Map.of(), null),
                                CALL_TOOL_RESULT_TYPE)
                            .block()));

    assertThat(thrown).isInstanceOf(McpError.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call missing-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.create(StatusCode.ERROR, "Method not found"))
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "missing-tool"),
                            equalTo(JSONRPC_REQUEST_ID, transport.getToolRequest().id().toString()),
                            equalTo(ERROR_TYPE, "-32601"),
                            equalTo(RPC_RESPONSE_STATUS_CODE, "-32601"))));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void recordsToolResultError() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.TOOL_ERROR);
    McpClientSession session = newSession(transport);

    testing.runWithSpan(
        "parent",
        () ->
            session
                .sendRequest(
                    McpSchema.METHOD_TOOLS_CALL,
                    new McpSchema.CallToolRequest("failing-tool", Map.of(), null),
                    CALL_TOOL_RESULT_TYPE)
                .block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call failing-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MCP_METHOD_NAME, "tools/call"),
                            equalTo(GEN_AI_OPERATION_NAME, "execute_tool"),
                            equalTo(GEN_AI_TOOL_NAME, "failing-tool"),
                            equalTo(JSONRPC_REQUEST_ID, transport.getToolRequest().id().toString()),
                            equalTo(ERROR_TYPE, "tool_error"))));
  }

  @Test
  void endsSpanOnCancellation() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.NONE);
    McpClientSession session = newSession(transport);

    Disposable subscription =
        testing.runWithSpan(
            "parent",
            () ->
                session
                    .sendRequest(
                        McpSchema.METHOD_TOOLS_CALL,
                        new McpSchema.CallToolRequest("slow-tool", Map.of(), null),
                        CALL_TOOL_RESULT_TYPE)
                    .subscribe());
    subscription.dispose();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("tools/call slow-tool")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void doesNotStartSpanWithoutSubscription() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);

    Mono<McpSchema.CallToolResult> publisher =
        testing.runWithSpan(
            "parent",
            () ->
                session.sendRequest(
                    McpSchema.METHOD_TOOLS_CALL,
                    new McpSchema.CallToolRequest("unused-tool", Map.of(), null),
                    CALL_TOOL_RESULT_TYPE));

    assertThat(publisher).isNotNull();
    assertThat(transport.getToolRequest()).isNull();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
  }

  @Test
  void doesNotTraceOtherMcpMethods() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);
    TypeRef<Object> responseType = new TypeRef<Object>() {};

    testing.runWithSpan("parent", () -> session.sendRequest("ping", null, responseType).block());

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
  }

  private static McpClientSession newSession(TestMcpClientTransport transport) {
    return new McpClientSession(
        Duration.ofSeconds(1), transport, Map.of(), Map.of(), Function.identity());
  }
}
