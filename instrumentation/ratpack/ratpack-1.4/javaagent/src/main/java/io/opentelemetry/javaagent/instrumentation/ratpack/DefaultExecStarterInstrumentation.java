/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.func.Action;

public class DefaultExecStarterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("ratpack.exec.internal.DefaultExecController$")
        .and(implementsInterface(named("ratpack.exec.ExecStarter")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onComplete")
            .or(named("onError"))
            .or(named("onStart"))
            .and(takesArgument(0, named("ratpack.func.Action"))),
        DefaultExecStarterInstrumentation.class.getName() + "$WrapActionAdvice");
    transformer.applyAdviceToMethod(
        named("start").and(takesArgument(0, named("ratpack.func.Action"))),
        DefaultExecStarterInstrumentation.class.getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapActionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapAction(@Advice.Argument(value = 0, readOnly = false) Action<?> action) {
      action = ActionWrapper.wrapIfNeeded(action);
    }
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.Argument(value = 0, readOnly = false) Action<?> action) {
      action = ActionWrapper.wrapIfNeeded(action);
      return Java8BytecodeBridge.rootContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
