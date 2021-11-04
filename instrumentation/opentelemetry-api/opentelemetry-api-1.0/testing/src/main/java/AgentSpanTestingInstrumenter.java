/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKey;
import java.lang.reflect.Field;

public final class AgentSpanTestingInstrumenter {

  private static final ContextKey<Request> REQUEST_CONTEXT_KEY =
      ContextKey.named("test-request-key");

  private static final Instrumenter<Request, Void> INSTRUMENTER =
      Instrumenter.<Request, Void>builder(
              GlobalOpenTelemetry.get(), "test", request -> request.name)
          .addContextCustomizer(
              (context, request, startAttributes) -> context.with(REQUEST_CONTEXT_KEY, request))
          .newInstrumenter(request -> request.kind);

  public static Context startServerSpan(String name) {
    return start(name, SpanKind.SERVER);
  }

  public static Context startClientSpan(String name) {
    return start(name, SpanKind.CLIENT);
  }

  public static Context startSpanWithAllKeys(String name) {
    Context context = start(name, SpanKind.INTERNAL);
    Span span = Span.fromContext(context);
    for (SpanKey spanKey : SpanKeyAccess.getSpanKeys()) {
      context = spanKey.storeInContext(context, span);
    }
    return context;
  }

  private static Context start(String name, SpanKind kind) {
    return INSTRUMENTER.start(Context.current(), new Request(name, kind));
  }

  public static void end(Context context, Throwable error) {
    INSTRUMENTER.end(context, context.get(REQUEST_CONTEXT_KEY), null, error);
  }

  private static final class Request {
    private final String name;
    private final SpanKind kind;

    public Request(String name, SpanKind kind) {
      this.name = name;
      this.kind = kind;
    }
  }

  private static final class SpanKeyAccess {

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

  private AgentSpanTestingInstrumenter() {}
}
