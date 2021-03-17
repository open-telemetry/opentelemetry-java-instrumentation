package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;

public abstract class ClientInstrumenter<REQUEST, CARRIER, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapSetter<CARRIER> setter;

  protected ClientInstrumenter(OpenTelemetry openTelemetry, String instrumentationName,
      TextMapSetter<CARRIER> setter,
      Iterable<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors) {
    super(openTelemetry.getTracer(instrumentationName), attributesExtractors);
    propagators = openTelemetry.getPropagators();
    this.setter = setter;
  }

  public Context start(Context parentContext, REQUEST request, CARRIER carrier) {
    Context newContext = super.start(parentContext, request);
    propagators.getTextMapPropagator().inject(newContext, carrier, setter);
    return newContext;
  }

  @Override
  protected final SpanKind spanKind(REQUEST request) {
    return SpanKind.CLIENT;
  }
}
