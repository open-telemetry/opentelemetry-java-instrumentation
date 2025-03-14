/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otel4s.v0_12;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.otel4s.FiberLocalContextHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("IdentifierName")
public class Otel4sIOLocalContextStorageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.typelevel.otel4s.oteljava.context.IOLocalContextStorage$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isMethod())
            .and(named("registerFiberThreadLocalContext"))
            .and(takesArgument(0, ThreadLocal.class)),
        this.getClass().getName() + "$RegisterAdvice");
  }

  @SuppressWarnings("unused")
  public static final class RegisterAdvice {

    private RegisterAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) ThreadLocal<Context> fiberThreadLocalContext) {
      FiberLocalContextHelper.setFiberThreadLocalContext(
          Otel4sFiberContextBridge.contextThreadLocal(fiberThreadLocalContext));
    }
  }
}
