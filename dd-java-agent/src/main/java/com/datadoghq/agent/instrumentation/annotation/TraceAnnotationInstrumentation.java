package com.datadoghq.agent.instrumentation.annotation;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;

import com.datadoghq.trace.Trace;
import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
import io.opentracing.ActiveSpan;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class TraceAnnotationInstrumentation implements Instrumenter {
  public static final Map<PreparedStatement, String> preparedStatements = new WeakHashMap<>();

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(hasSuperType(declaresMethod(isAnnotatedWith(Trace.class))))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(isAnnotatedWith(Trace.class), TraceAdvice.class.getName()));
  }

  public static class TraceAdvice {

    @Advice.OnMethodEnter
    public static ActiveSpan startSpan(@Advice.Origin final Method method) {
      final Trace trace = method.getAnnotation(Trace.class);
      String operationName = trace.operationName();
      if (operationName == null || operationName.isEmpty()) {
        operationName = method.getDeclaringClass().getName() + "." + method.getName();
      }

      return GlobalTracer.get().buildSpan(operationName).startActive();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final ActiveSpan activeSpan, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        Tags.ERROR.set(activeSpan, true);
        activeSpan.log(Collections.singletonMap("error.object", throwable));
      }
      activeSpan.deactivate();
    }
  }
}
