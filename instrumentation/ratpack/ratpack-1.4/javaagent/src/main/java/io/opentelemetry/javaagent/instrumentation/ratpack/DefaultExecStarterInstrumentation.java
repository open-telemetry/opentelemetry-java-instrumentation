/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
        namedOneOf("onComplete", "onError", "onStart")
            .and(takesArgument(0, named("ratpack.func.Action"))),
        DefaultExecStarterInstrumentation.class.getName() + "$WrapActionAdvice");
    transformer.applyAdviceToMethod(
        named("start").and(takesArgument(0, named("ratpack.func.Action"))),
        DefaultExecStarterInstrumentation.class.getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapActionAdvice {
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Action<?> wrapAction(@Advice.Argument(0) Action<?> action) {
      return ActionWrapper.wrapIfNeeded(action);
    }
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {
    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enter(@Advice.Argument(0) Action<?> action) {

      // wrapping method is relying on current context
      // thus we must wrap first to avoid using the root context
      Action<?> wrappedAction = ActionWrapper.wrapIfNeeded(action);

      // root context scope must be made current after wrapping
      Scope scope = Java8BytecodeBridge.rootContext().makeCurrent();
      return new Object[] {scope, wrappedAction};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Object[] enterResult) {
      Scope scope = (Scope) enterResult[0];
      if (scope != null) {
        scope.close();
      }
    }
  }
}
