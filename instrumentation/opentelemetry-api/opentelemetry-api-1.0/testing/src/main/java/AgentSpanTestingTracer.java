/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKey;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Field;

public class AgentSpanTestingTracer extends BaseTracer {
  private static final AgentSpanTestingTracer TRACER = new AgentSpanTestingTracer();

  private AgentSpanTestingTracer() {
    super(GlobalOpenTelemetry.get());
  }

  public static AgentSpanTestingTracer tracer() {
    return TRACER;
  }

  public Context startServerSpan(String name) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder = spanBuilder(parentContext, name, SpanKind.SERVER);
    return withServerSpan(parentContext, spanBuilder.startSpan());
  }

  public Context startConsumerSpan(String name) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder = spanBuilder(parentContext, name, SpanKind.CONSUMER);
    return withConsumerSpan(parentContext, spanBuilder.startSpan());
  }

  public Context startClientSpan(String name) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder = spanBuilder(parentContext, name, SpanKind.CLIENT);
    return withClientSpan(parentContext, spanBuilder.startSpan());
  }

  public Context startSpanWithAllKeys(String name) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder = spanBuilder(parentContext, name, SpanKind.INTERNAL);
    Span span = spanBuilder.startSpan();
    Context newContext = parentContext.with(span);
    for (SpanKey spanKey : SpanKeyAccess.getSpanKeys()) {
      newContext = spanKey.storeInContext(newContext, span);
    }
    return newContext;
  }

  @Override
  protected String getInstrumentationName() {
    return "agent-span-test-instrumentation";
  }

  private static class SpanKeyAccess {

    public static SpanKey[] getSpanKeys() {
      return new SpanKey[] {
        SpanKey.SERVER,
        getSpanKeyByName("HTTP_CLIENT"),
        getSpanKeyByName("RPC_CLIENT"),
        getSpanKeyByName("DB_CLIENT"),
        SpanKey.ALL_CLIENTS,
        getSpanKeyByName("PRODUCER"),
        getSpanKeyByName("CONSUMER_RECEIVE"),
        getSpanKeyByName("CONSUMER_PROCESS")
      };
    }

    private static SpanKey getSpanKeyByName(String name) {
      try {
        Field field = SpanKey.class.getDeclaredField(name);
        field.setAccessible(true);
        return (SpanKey) field.get(name);
      } catch (NoSuchFieldException | IllegalAccessException exception) {
        throw new IllegalStateException("Failed to find span key named " + name, exception);
      }
    }
  }
}
