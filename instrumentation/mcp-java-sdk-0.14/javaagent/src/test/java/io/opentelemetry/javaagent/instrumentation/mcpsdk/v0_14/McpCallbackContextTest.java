/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

class McpCallbackContextTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @EnumSource(
      value = TestMcpClientTransport.ToolResponse.class,
      names = {"SUCCESS", "JSON_RPC_ERROR"})
  void restoresRootContextForCallbacks(TestMcpClientTransport.ToolResponse response) {
    McpAsyncClient first = McpClient.async(new TestMcpClientTransport(response)).build();
    McpAsyncClient second =
        McpClient.async(new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS))
            .build();
    first.initialize().block();
    second.initialize().block();
    List<Context> callbackContexts = new ArrayList<>();
    Mono<McpSchema.CallToolResult> publisher =
        first
            .callTool(new McpSchema.CallToolRequest("first", Map.of(), null))
            .doOnSubscribe(unused -> callbackContexts.add(Context.current()))
            .doOnNext(result -> {
              callbackContexts.add(Context.current());
              second.callTool(new McpSchema.CallToolRequest("second", Map.of(), null)).subscribe();
            })
            .doOnError(unused -> callbackContexts.add(Context.current()))
            .onErrorResume(
                McpError.class,
                error -> {
                  callbackContexts.add(Context.current());
                  return second.callTool(new McpSchema.CallToolRequest("second", Map.of(), null));
                })
            .doOnSuccess(unused -> callbackContexts.add(Context.current()));
    try {
      assertThat(publisher.block()).isNotNull();
    } finally {
      first.close();
      second.close();
    }
    assertThat(callbackContexts).isNotEmpty();
    assertThat(callbackContexts)
        .allSatisfy(
            context -> assertThat(Span.fromContext(context).getSpanContext().isValid()).isFalse());
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call first").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call second").hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void preservesCallerValuesAndRestoresDeferredDemandThread() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.SUCCESS);
    McpAsyncClient client = McpClient.async(transport).build();
    client.initialize().block();
    ContextKey<String> key = ContextKey.named("caller-value");
    Context caller =
        Baggage.builder()
            .put("tenant", "caller")
            .build()
            .storeInContext(Context.root().with(key, "custom"));
    AtomicReference<Subscription> subscription = new AtomicReference<>();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    AtomicReference<Context> completionContext = new AtomicReference<>();
    BaseSubscriber<McpSchema.CallToolResult> subscriber =
        new BaseSubscriber<>() {
          @Override
          protected void hookOnSubscribe(Subscription value) {
            subscription.set(value);
          }

          @Override
          protected void hookOnNext(McpSchema.CallToolResult result) {
            callbackContext.set(Context.current());
            testing.runWithSpan("callback", () -> {});
          }

          @Override
          protected void hookOnComplete() {
            completionContext.set(Context.current());
          }
        };
    try {
      ContextPropagationOperator.runWithContext(
              client.callTool(new McpSchema.CallToolRequest("demand", Map.of(), null)), caller)
          .subscribe(subscriber);
      assertThat(transport.getToolRequest()).isNull();
      assertThat(callbackContext.get()).isNull();
      testing.runWithSpan(
          "demand-thread",
          () -> {
            Context threadContext = Context.current();
            subscription.get().request(1);
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(threadContext).getSpanContext());
          });
      assertThat(callbackContext.get().get(key)).isEqualTo("custom");
      assertThat(Baggage.fromContext(callbackContext.get()).getEntryValue("tenant"))
          .isEqualTo("caller");
      assertThat(Span.fromContext(callbackContext.get()).getSpanContext().isValid()).isFalse();
      assertThat(completionContext.get().get(key)).isEqualTo("custom");
      assertThat(Span.fromContext(transport.getToolRequestContext()).getSpanContext().isValid())
          .isTrue();
    } finally {
      subscriber.dispose();
      client.close();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("demand-thread").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call demand").hasKind(CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("callback").hasNoParent()));
  }

  @Test
  void restoresUnrelatedResponseThread() {
    TestMcpClientTransport transport =
        new TestMcpClientTransport(TestMcpClientTransport.ToolResponse.NONE);
    McpAsyncClient client = McpClient.async(transport).build();
    client.initialize().block();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    try {
      client
          .callTool(new McpSchema.CallToolRequest("async", Map.of(), null))
          .doOnNext(result -> {
            callbackContext.set(Context.current());
            testing.runWithSpan("callback", () -> {});
          })
          .subscribe();
      testing.runWithSpan(
          "response-thread",
          () -> {
            Context threadContext = Context.current();
            transport.respondToToolRequest().block();
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(threadContext).getSpanContext());
          });
      assertThat(Span.fromContext(callbackContext.get()).getSpanContext().isValid()).isFalse();
    } finally {
      client.close();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("tools/call async").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("response-thread").hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("callback").hasNoParent()));
  }
}
