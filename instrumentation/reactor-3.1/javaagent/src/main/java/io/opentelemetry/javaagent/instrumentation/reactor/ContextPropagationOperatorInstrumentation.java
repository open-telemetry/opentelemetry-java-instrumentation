package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import application.io.opentelemetry.context.Context;

import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ContextPropagationOperatorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameEndsWith("ContextPropagationOperator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("storeOpenTelemetryContext"))
            .and(takesArgument(0, named("reactor.util.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("reactor.util.context.Context"))),
        ContextPropagationOperatorInstrumentation.class.getName() + "$StoreAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("getOpenTelemetryContext"))
            .and(takesArgument(0, named("reactor.util.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("application.io.opentelemetry.context.Context"))),
        ContextPropagationOperatorInstrumentation.class.getName() + "$GetAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("runWithContext"))
            .and(takesArgument(0, named("reactor.core.publisher.Mono")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        ContextPropagationOperatorInstrumentation.class.getName() + "$RunMonoAdvice");
  }

  @SuppressWarnings("unused")
  public static class StoreAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnDefaultValue.class)
    public static boolean methodEnter() {
      return false;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) reactor.util.context.Context reactorContext,
        @Advice.Argument(1) Context applicationContext,
        @Advice.Return(readOnly = false) reactor.util.context.Context updatedReactorContext) {
      updatedReactorContext = ContextPropagationOperator.storeOpenTelemetryContext(reactorContext, AgentContextStorage.getAgentContext(applicationContext));
    }
  }

  @SuppressWarnings("unused")
  public static class GetAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static boolean methodEnter() {
      return false;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) reactor.util.context.Context reactorContext,
        @Advice.Argument(1) Context defaultContext,
        @Advice.Return(readOnly = false) Context applicationContext) {

      io.opentelemetry.context.Context agentContext = ContextPropagationOperator.getOpenTelemetryContext(reactorContext, null);
      if (agentContext == null) {
        applicationContext = defaultContext;
      } else {
        applicationContext = AgentContextStorage.toApplicationContext(agentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunMonoAdvice {
    @Advice.OnMethodEnter
    public static void methodEnter(
        @Advice.FieldValue(value = "enabled", readOnly = false) boolean enabled) {
      enabled = true;
    }
  }
}
