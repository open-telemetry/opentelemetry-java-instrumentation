/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import javax.annotation.Nullable;

public final class AgentSpanTestingInstrumenter {

  private static final ContextKey<Request> REQUEST_CONTEXT_KEY =
      ContextKey.named("test-request-key");

  private static final Instrumenter<Request, Void> INSTRUMENTER =
      Instrumenter.<Request, Void>builder(
              GlobalOpenTelemetry.get(), "test", request -> request.name)
          .addContextCustomizer(
              (context, request, startAttributes) -> context.with(REQUEST_CONTEXT_KEY, request))
          .newInstrumenter(request -> request.kind);

  private static final Instrumenter<Request, Void> INSTRUMENTER_HTTP_SERVER =
      Instrumenter.<Request, Void>builder(
              GlobalOpenTelemetry.get(), "test", request -> request.name)
          .addAttributesExtractor(HttpServerSpanKeyAttributesExtractor.INSTANCE)
          .addContextCustomizer(
              (context, request, startAttributes) -> context.with(REQUEST_CONTEXT_KEY, request))
          .newInstrumenter(request -> request.kind);

  public static Context startHttpServerSpan(String name) {
    return INSTRUMENTER_HTTP_SERVER.start(Context.current(), new Request(name, SpanKind.SERVER));
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

  public static void endHttpServer(Context context, Throwable error) {
    INSTRUMENTER_HTTP_SERVER.end(context, context.get(REQUEST_CONTEXT_KEY), null, error);
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
      // span kind keys
      SpanKey.KIND_SERVER,
      SpanKey.KIND_CLIENT,
      SpanKey.KIND_CONSUMER,
      SpanKey.KIND_PRODUCER,
      // semantic convention keys
      SpanKey.HTTP_SERVER,
      SpanKey.RPC_SERVER,
      SpanKey.HTTP_CLIENT,
      SpanKey.RPC_CLIENT,
      SpanKey.DB_CLIENT,
      SpanKey.PRODUCER,
      SpanKey.CONSUMER_RECEIVE,
      SpanKey.CONSUMER_PROCESS,
    };
  }

  // simulate a real HTTP server implementation
  private enum HttpServerSpanKeyAttributesExtractor
      implements AttributesExtractor<Request, Void>, SpanKeyProvider {
    INSTANCE;

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Request request,
        @Nullable Void unused,
        @Nullable Throwable error) {}

    @Override
    public SpanKey internalGetSpanKey() {
      return SpanKey.HTTP_SERVER;
    }
  }

  private AgentSpanTestingInstrumenter() {}
}
