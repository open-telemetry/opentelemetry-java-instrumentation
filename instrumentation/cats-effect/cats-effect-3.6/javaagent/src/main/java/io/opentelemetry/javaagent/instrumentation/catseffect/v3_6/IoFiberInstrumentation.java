/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.catseffect.common.v3_6.IoLocalContextSingleton;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IoFiberInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("cats.effect.IOFiber");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("scala.collection.immutable.Map")))
            .and(takesArgument(1, named("scala.Function1")))
            .and(takesArgument(2, named("cats.effect.IO")))
            .and(takesArgument(3, named("scala.concurrent.ExecutionContext")))
            .and(takesArgument(4, named("cats.effect.unsafe.IORuntime"))),
        this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static final class ConstructorAdvice {
    private ConstructorAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 2, readOnly = false) cats.effect.IO<?> io) {
      io = IoLocalContextSingleton.ioLocal.asLocal().scope(io, Context.current());
    }
  }
}
