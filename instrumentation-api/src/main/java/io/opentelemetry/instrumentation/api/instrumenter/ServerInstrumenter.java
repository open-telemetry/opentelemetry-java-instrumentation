package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;

public abstract class ServerInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  protected ServerInstrumenter(OpenTelemetry openTelemetry, String instrumentationName, TextMapGetter<REQUEST> getter,
      Iterable<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors) {
    super(openTelemetry.getTracer(instrumentationName), attributesExtractors);
    propagators = openTelemetry.getPropagators();
    this.getter = getter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    return super.start(extracted, request);
  }

  @Override
  protected final SpanKind spanKind(REQUEST request) {
    return SpanKind.SERVER;
  }
}
