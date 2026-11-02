/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1.operator;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ContextPropagationOperatorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "application.io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("storeOpenTelemetryContext"))
            .and(takesArgument(0, named("reactor.util.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("reactor.util.context.Context"))),
        getClass().getName() + "$StoreAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("getOpenTelemetryContext"))
            .and(takesArgument(0, named("reactor.util.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("application.io.opentelemetry.context.Context"))),
        getClass().getName() + "$GetAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("withoutSubscriptionContextCapture"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("reactor.util.context.Context"))),
        getClass().getName() + "$WithoutSubscriptionContextCaptureAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("runWithContext"))
            .and(takesArguments(2))
            .and(
                takesArgument(
                    0, namedOneOf("reactor.core.publisher.Mono", "reactor.core.publisher.Flux")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(namedOneOf("reactor.core.publisher.Mono", "reactor.core.publisher.Flux"))),
        getClass().getName() + "$RunWithAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("runWithContext"))
            .and(takesArguments(3))
            .and(takesArgument(0, named("reactor.core.publisher.Mono")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(takesArgument(2, named("application.io.opentelemetry.context.Context"))),
        getClass().getName() + "$RunMonoWithContextsAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("runWithContext"))
            .and(takesArguments(3))
            .and(takesArgument(0, named("reactor.core.publisher.Flux")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(takesArgument(2, named("application.io.opentelemetry.context.Context"))),
        getClass().getName() + "$RunFluxWithContextsAdvice");
  }

  @SuppressWarnings("unused")
  public static class WithoutSubscriptionContextCaptureAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static reactor.util.context.Context methodExit(
        @Advice.Return reactor.util.context.Context reactorContext) {
      return ContextPropagationOperator.withoutSubscriptionContextCapture(reactorContext);
    }
  }

  @SuppressWarnings("unused")
  public static class RunMonoWithContextsAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, inline = false)
    public static boolean methodEnter() {
      return false;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Mono<?> methodExit(
        @Advice.Argument(0) Mono<?> publisher,
        @Advice.Argument(1) application.io.opentelemetry.context.Context operationContext,
        @Advice.Argument(2) application.io.opentelemetry.context.Context applicationContext) {
      return ContextPropagationOperator.runWithContext(
          publisher,
          AgentContextStorage.getAgentContext(operationContext),
          AgentContextStorage.getAgentContext(applicationContext));
    }
  }

  @SuppressWarnings("unused")
  public static class RunFluxWithContextsAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, inline = false)
    public static boolean methodEnter() {
      return false;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Flux<?> methodExit(
        @Advice.Argument(0) Flux<?> publisher,
        @Advice.Argument(1) application.io.opentelemetry.context.Context operationContext,
        @Advice.Argument(2) application.io.opentelemetry.context.Context applicationContext) {
      return ContextPropagationOperator.runWithContext(
          publisher,
          AgentContextStorage.getAgentContext(operationContext),
          AgentContextStorage.getAgentContext(applicationContext));
    }
  }

  @SuppressWarnings("unused")
  public static class StoreAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, inline = false)
    public static boolean methodEnter() {
      return false;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static reactor.util.context.Context methodExit(
        @Advice.Argument(0) reactor.util.context.Context reactorContext,
        @Advice.Argument(1) application.io.opentelemetry.context.Context applicationContext) {
      return ContextPropagationOperator.storeOpenTelemetryContext(
          reactorContext, AgentContextStorage.getAgentContext(applicationContext));
    }
  }

  @SuppressWarnings("unused")
  public static class GetAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, inline = false)
    public static boolean methodEnter() {
      return false;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static application.io.opentelemetry.context.Context methodExit(
        @Advice.Argument(0) reactor.util.context.Context reactorContext,
        @Advice.Argument(1) application.io.opentelemetry.context.Context defaultContext) {

      io.opentelemetry.context.Context agentContext =
          ContextPropagationOperator.getOpenTelemetryContext(reactorContext, null);
      if (agentContext == null) {
        return defaultContext;
      }

      return AgentContextStorage.toApplicationContext(agentContext);
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithAdvice {
    @Advice.OnMethodEnter(inline = false)
    @Advice.AssignReturned.ToFields(@Advice.AssignReturned.ToFields.ToField("enabled"))
    public static boolean methodEnter() {
      return true;
    }
  }
}
