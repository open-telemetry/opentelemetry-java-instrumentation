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
import io.opentelemetry.instrumentation.api.internal.SpanKey;

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
    for (SpanKey spanKey : getSpanKeys()) {
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

  private static SpanKey[] getSpanKeys() {
    return new SpanKey[] {
      SpanKey.SERVER,
      SpanKey.HTTP_CLIENT,
      SpanKey.RPC_CLIENT,
      SpanKey.DB_CLIENT,
      SpanKey.ALL_CLIENTS,
      SpanKey.PRODUCER,
      SpanKey.CONSUMER_RECEIVE,
      SpanKey.CONSUMER_PROCESS
    };
  }

  private AgentSpanTestingInstrumenter() {}
}
