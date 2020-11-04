package io.opentelemetry.javaagent.instrumentation.struts2;

import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;
import org.apache.struts2.dispatcher.mapper.ActionMapping;

public class ExecuteActionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(2) ActionMapping actionMapping,
      @Advice.Local("otelSpan") Span span) {
    span = Struts2Tracer.TRACER.startSpan(actionMapping);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelSpan") Span span) {
    if (throwable != null) {
      span.recordException(throwable);
    }
    span.end();
  }
}
