/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import cats.effect.unsafe.IORuntime;
import io.opentelemetry.javaagent.bootstrap.catseffect.v3_6.FiberLocalContextHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.catseffect.common.v3_6.IoLocalContextSingleton;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IoRuntimeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("cats.effect.unsafe.IORuntime");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static final class ConstructorAdvice {
    private ConstructorAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      FiberLocalContextHelper.initialize(
          IoLocalContextSingleton.contextThreadLocal, IORuntime::isUnderFiberContext);
    }
  }
}
