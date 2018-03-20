package datadog.trace.instrumentation.trace_annotation;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Trace;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class TraceAnnotationInstrumentation extends Instrumenter.Configurable {

  public TraceAnnotationInstrumentation() {
    super("trace", "trace-annotation");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(failSafe(hasSuperType(declaresMethod(isAnnotatedWith(Trace.class)))))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create().advice(isAnnotatedWith(Trace.class), TraceAdvice.class.getName()))
        .asDecorator();
  }

  public static class TraceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Origin final Method method) {
      final Trace trace = method.getAnnotation(Trace.class);
      String operationName = trace == null ? null : trace.operationName();
      if (operationName == null || operationName.isEmpty()) {
        operationName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
      }

      return GlobalTracer.get().buildSpan(operationName).startActive(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
