/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.catseffect.common.v3_6.IoLocalContextSingleton;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IoInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("cats.effect.IO");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("unsafeRunFiber")),
        this.getClass().getName() + "$UnsafeRunFiberAdvice");
  }

  @SuppressWarnings("unused")
  public static final class UnsafeRunFiberAdvice {
    private UnsafeRunFiberAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This(readOnly = false) cats.effect.IO<?> io) {
      io = IoLocalContextSingleton.ioLocal.asLocal().scope(io, Context.current());
    }
  }
}
