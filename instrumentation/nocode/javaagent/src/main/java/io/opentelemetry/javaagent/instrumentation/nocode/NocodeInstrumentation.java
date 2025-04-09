/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.nocode.NocodeSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeInstrumentationRules;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaConstant;

public final class NocodeInstrumentation implements TypeInstrumentation {
  private final NocodeInstrumentationRules.Rule rule;

  public NocodeInstrumentation(NocodeInstrumentationRules.Rule rule) {
    this.rule = rule;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // null rule is used when no rules are configured, this ensures that muzzle can collect helper
    // classes
    if (rule == null) {
      return none();
    }
    // methods instrumentation also uses hasSuperType
    return hasSuperType(named(rule.getClassName()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        rule != null ? named(rule.getMethodName()) : none(),
        mapping ->
            mapping.bind(
                RuleId.class, JavaConstant.Simple.ofLoaded(rule != null ? rule.getId() : -1)),
        this.getClass().getName() + "$NocodeAdvice");
  }

  @interface RuleId {}

  @SuppressWarnings("unused")
  public static class NocodeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @RuleId int ruleId,
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelInvocation") NocodeMethodInvocation otelInvocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.This Object thiz,
        @Advice.AllArguments Object[] methodParams) {
      NocodeInstrumentationRules.Rule rule = NocodeInstrumentationRules.find(ruleId);
      otelInvocation =
          new NocodeMethodInvocation(
              rule, ClassAndMethod.create(declaringClass, methodName), thiz, methodParams);
      Context parentContext = currentContext();

      if (!instrumenter().shouldStart(parentContext, otelInvocation)) {
        return;
      }
      context = instrumenter().start(parentContext, otelInvocation);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Origin Method method,
        @Advice.Local("otelInvocation") NocodeMethodInvocation otelInvocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable error) {
      if (scope == null) {
        return;
      }
      scope.close();

      returnValue =
          AsyncOperationEndSupport.create(instrumenter(), Object.class, method.getReturnType())
              .asyncEnd(context, otelInvocation, returnValue, error);
    }
  }
}
