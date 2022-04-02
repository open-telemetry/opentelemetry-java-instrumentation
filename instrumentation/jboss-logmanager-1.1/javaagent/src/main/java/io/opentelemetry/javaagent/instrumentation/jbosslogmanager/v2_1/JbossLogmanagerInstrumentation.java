package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.v2_1;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Logger;

public class JbossLogmanagerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.logmanager.Logger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("logRaw"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.jboss.logmanager.ExtLogRecord"))),
        JbossLogmanagerInstrumentation.class.getName() + "$CallLogRawAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallLogRawAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This() Logger logger,
        @Advice.Argument(0) ExtLogRecord record,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        LoggingEventMapper.INSTANCE.capture(logger, record);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
