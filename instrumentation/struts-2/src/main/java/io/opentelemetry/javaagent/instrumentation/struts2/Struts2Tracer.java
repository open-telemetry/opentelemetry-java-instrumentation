package io.opentelemetry.javaagent.instrumentation.struts2;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import org.apache.struts2.dispatcher.mapper.ActionMapping;

public class Struts2Tracer extends BaseTracer {

  public static final Struts2Tracer TRACER = new Struts2Tracer();

  public Span startSpan(ActionMapping actionMapping) {
//    String spanName = namespace + name;
//    if (method != null) {
//      spanName += "." + method;
//    }
    return tracer.spanBuilder(actionMapping.toString()).startSpan();
  }

  public void endSpan() {
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.struts-2";
  }
}
