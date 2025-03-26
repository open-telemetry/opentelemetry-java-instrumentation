/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.nocode.NocodeSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeInstrumentationRules;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public final class NocodeInstrumentation implements TypeInstrumentation {
  private final NocodeInstrumentationRules.Rule rule;

  public NocodeInstrumentation(NocodeInstrumentationRules.Rule rule) {
    this.rule = rule;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // names have to match exactly for now to enable rule lookup
    // at advice time.  In the future, we could support
    // more complex rules here if we dynamically generated advice classes for
    // each rule or otherwise parameterized the inserted bytecode by rule.
    return rule != null ? named(rule.getClassName()) : none();
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        rule != null ? named(rule.getMethodName()) : none(),
        this.getClass().getName() + "$NocodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class NocodeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelInvocation") NocodeMethodInvocation otelInvocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.This Object thiz,
        @Advice.AllArguments Object[] methodParams) {
      NocodeInstrumentationRules.Rule rule =
          NocodeInstrumentationRules.find(declaringClass.getName(), methodName);
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
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returnValue,
        @Advice.Thrown Throwable error) {
      if (scope == null) {
        return;
      }
      scope.close();
      // This is heavily based on the "methods" instrumentation, but
      // for now we're not supporting modifying return types/async result codes, etc.
      // This could be expanded in the future.
      instrumenter().end(context, otelInvocation, returnValue, error);
    }
  }
}
