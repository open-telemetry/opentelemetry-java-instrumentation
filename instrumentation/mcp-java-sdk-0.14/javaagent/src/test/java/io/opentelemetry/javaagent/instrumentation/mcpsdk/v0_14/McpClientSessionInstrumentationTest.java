/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EXECUTE_TOOL;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.incubating.McpIncubatingMetrics.MCP_CLIENT_OPERATION_DURATION_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class McpClientSessionInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.mcp-java-sdk-0.14";
  private static final TypeRef<McpSchema.CallToolResult> CALL_TOOL_RESULT_TYPE =
      new TypeRef<McpSchema.CallToolResult>() {};
  private static final TypeRef<McpSchema.InitializeResult> INITIALIZE_RESULT_TYPE =
      new TypeRef<McpSchema.InitializeResult>() {};

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

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("tools/call delete-file")
                    .hasKind(CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        equalTo(MCP_METHOD_NAME, "tools/call"),
                        equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
                        equalTo(GEN_AI_TOOL_NAME, "delete-file"),
                        equalTo(JSONRPC_REQUEST_ID, transport.getToolRequest().id().toString()))));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void recordsDurationWithNegotiatedProtocolVersion() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);

    Mono<McpSchema.InitializeResult> initialize =
        session.sendRequest(McpSchema.METHOD_INITIALIZE, null, INITIALIZE_RESULT_TYPE);
    Mono<McpSchema.CallToolResult> callTool =
        session.sendRequest(
            McpSchema.METHOD_TOOLS_CALL,
            new McpSchema.CallToolRequest("lookup", Map.of(), null),
            CALL_TOOL_RESULT_TYPE);

    assertThat(initialize.then(callTool).block()).isNotNull();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("tools/call lookup")
                .hasKind(CLIENT)
                .hasNoParent()
                .hasAttribute(MCP_PROTOCOL_VERSION, ProtocolVersions.MCP_2024_11_05)));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("mcp.client.operation.duration")
                .hasUnit("s")
                .hasDescription(MCP_CLIENT_OPERATION_DURATION_DESCRIPTION)
                .hasHistogramSatisfying(histogram ->
                    histogram.hasPointsSatisfying(point ->
                        point
                            .hasAttributesSatisfyingExactly(
                                equalTo(MCP_METHOD_NAME, "tools/call"),
                                equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
                                equalTo(GEN_AI_TOOL_NAME, "lookup"),
                                equalTo(MCP_PROTOCOL_VERSION, ProtocolVersions.MCP_2024_11_05))
                            .hasBucketBoundaries(
                                0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 30, 60, 120, 300))));
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

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("tools/call async-tool").hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void propagatesToolCallContextThroughReactorPublisher() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);

    assertThat(
            session
                .sendRequest(
                    McpSchema.METHOD_TOOLS_CALL,
                    new McpSchema.CallToolRequest("context-tool", Map.of(), null),
                    CALL_TOOL_RESULT_TYPE)
                .block())
        .isNotNull();

    assertThat(transport.getToolRequestContext()).isNotNull();
    SpanContext propagatedSpanContext =
        Span.fromContext(transport.getToolRequestContext()).getSpanContext();
    assertThat(propagatedSpanContext.isValid()).isTrue();
    testing.waitAndAssertTraces(trace -> {
      trace.hasSpansSatisfyingExactly(
          span -> span.hasName("tools/call context-tool").hasKind(CLIENT).hasNoParent());
      assertThat(propagatedSpanContext.getTraceId()).isEqualTo(trace.getSpan(0).getTraceId());
      assertThat(propagatedSpanContext.getSpanId()).isEqualTo(trace.getSpan(0).getSpanId());
    });
  }

  @Test
  void isolatesMetadataAcrossSubscriptions() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);
    McpSchema.CallToolRequest original =
        new McpSchema.CallToolRequest("lookup", Map.of(), Map.of("tenant", "application"));
    Mono<McpSchema.CallToolResult> publisher =
        session.sendRequest(McpSchema.METHOD_TOOLS_CALL, original, CALL_TOOL_RESULT_TYPE);

    Context firstContext =
        Baggage.builder().put("tenant", "first").build().storeInContext(Context.root());
    assertThat(ContextPropagationOperator.runWithContext(publisher, firstContext).block())
        .isNotNull();
    McpSchema.CallToolRequest first =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(first.meta()).containsEntry("baggage", "tenant=first");
    String firstTraceparent = (String) first.meta().get("traceparent");
    Object requestId = transport.getToolRequest().id();

    Context secondContext =
        Baggage.builder().put("tenant", "second").build().storeInContext(Context.root());
    assertThat(ContextPropagationOperator.runWithContext(publisher, secondContext).block())
        .isNotNull();
    McpSchema.CallToolRequest second =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(second.meta()).containsEntry("baggage", "tenant=second");
    assertThat(transport.getToolRequest().id()).isEqualTo(requestId);

    assertThat(ContextPropagationOperator.runWithContext(publisher, Context.root()).block())
        .isNotNull();
    McpSchema.CallToolRequest third =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(third.meta()).containsEntry("tenant", "application").doesNotContainKey("baggage");
    assertThat(first.meta())
        .containsEntry("baggage", "tenant=first")
        .containsEntry("traceparent", firstTraceparent);
    assertThat(second.meta()).containsEntry("baggage", "tenant=second");
    assertThat(third.meta().get("traceparent"))
        .isNotEqualTo(firstTraceparent)
        .isNotEqualTo(second.meta().get("traceparent"));
    assertThat(original.meta()).containsExactlyEntriesOf(Map.of("tenant", "application"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call lookup").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call lookup").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call lookup").hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void preservesSessionOverrideAndRequestTransformations() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    AtomicInteger invocations = new AtomicInteger();
    AtomicInteger responses = new AtomicInteger();
    McpClientSession session =
        new McpClientSession(
            Duration.ofSeconds(1), transport, Map.of(), Map.of(), Function.identity()) {
          @Override
          public <T> Mono<T> sendRequest(
              String method, Object parameters, TypeRef<T> responseType) {
            invocations.incrementAndGet();
            McpSchema.CallToolRequest request = (McpSchema.CallToolRequest) parameters;
            return super.sendRequest(
                    method,
                    new McpSchema.CallToolRequest(
                        request.name(), request.arguments(), Map.of("tenant", "transformed")),
                    responseType)
                .doOnNext(unused -> responses.incrementAndGet());
          }
        };
    Mono<McpSchema.CallToolResult> publisher =
        session.sendRequest(
            McpSchema.METHOD_TOOLS_CALL,
            new McpSchema.CallToolRequest("override", Map.of(), null),
            CALL_TOOL_RESULT_TYPE);

    assertThat(invocations.get()).isEqualTo(1);
    assertThat(publisher.block()).isNotNull();
    assertThat(publisher.block()).isNotNull();
    assertThat(invocations.get()).isEqualTo(1);
    assertThat(responses.get()).isEqualTo(2);
    McpSchema.CallToolRequest sent =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(sent.meta()).containsEntry("tenant", "transformed").containsKey("traceparent");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call override").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call override").hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void injectsSubscriptionContextOnWorkerThread() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpClientSession session = newSession(transport);
    Mono<McpSchema.CallToolResult> publisher =
        session.sendRequest(
            McpSchema.METHOD_TOOLS_CALL,
            new McpSchema.CallToolRequest("worker", Map.of(), null),
            CALL_TOOL_RESULT_TYPE);
    testing.runWithSpan(
        "parent",
        () -> {
          Context context =
              Baggage.builder().put("tenant", "worker").build().storeInContext(Context.current());
          assertThat(
                  ContextPropagationOperator.runWithContext(publisher, context)
                      .subscribeOn(Schedulers.boundedElastic())
                      .block())
              .isNotNull();
        });
    McpSchema.CallToolRequest sent =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    assertThat(sent.meta()).containsEntry("baggage", "tenant=worker");
    testing.waitAndAssertTraces(trace -> {
      trace.hasSpansSatisfyingExactly(
          span -> span.hasName("parent").hasNoParent(),
          span -> span.hasName("tools/call worker").hasKind(CLIENT).hasParent(trace.getSpan(0)));
      assertThat(sent.meta().get("traceparent"))
          .isEqualTo(
              "00-"
                  + trace.getSpan(1).getTraceId()
                  + "-"
                  + trace.getSpan(1).getSpanId()
                  + "-"
                  + trace.getSpan(1).getSpanContext().getTraceFlags().asHex());
    });
  }

  @Test
  void isolatesMetadataForOverlappingSubscriptions() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.NONE);
    McpClientSession session = newSession(transport);
    Mono<McpSchema.CallToolResult> publisher =
        session.sendRequest(
            McpSchema.METHOD_TOOLS_CALL,
            new McpSchema.CallToolRequest("overlap", Map.of(), null),
            CALL_TOOL_RESULT_TYPE);
    Context firstContext =
        Baggage.builder().put("tenant", "first").build().storeInContext(Context.root());
    Disposable firstSubscription =
        ContextPropagationOperator.runWithContext(publisher, firstContext).subscribe();
    McpSchema.CallToolRequest first =
        (McpSchema.CallToolRequest) transport.getToolRequest().params();
    Disposable secondSubscription =
        ContextPropagationOperator.runWithContext(publisher, Context.root()).subscribe();
    try {
      McpSchema.CallToolRequest second =
          (McpSchema.CallToolRequest) transport.getToolRequest().params();
      assertThat(first.meta()).containsEntry("baggage", "tenant=first");
      assertThat(second.meta()).doesNotContainKey("baggage");
      assertThat(second).isNotSameAs(first);
    } finally {
      firstSubscription.dispose();
      secondSubscription.dispose();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call overlap").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call overlap").hasKind(CLIENT).hasNoParent()));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void recordsJsonRpcError() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.JSON_RPC_ERROR);
    McpClientSession session = newSession(transport);

    Throwable thrown =
        catchThrowable(() ->
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
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("tools/call missing-tool")
                    .hasKind(CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasStatus(StatusData.error())
                    .hasException(thrown)
                    .hasAttributesSatisfyingExactly(
                        equalTo(MCP_METHOD_NAME, "tools/call"),
                        equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
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

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("tools/call failing-tool")
                    .hasKind(CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasStatus(StatusData.error())
                    .hasAttributesSatisfyingExactly(
                        equalTo(MCP_METHOD_NAME, "tools/call"),
                        equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
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
    assertThat(testing.spans()).hasSize(1);
    subscription.dispose();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("tools/call slow-tool").hasKind(CLIENT).hasParent(trace.getSpan(0))));
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
