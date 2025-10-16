/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RxJavaHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.reactivex.sqlclient.Query");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("rxExecute"), RxJavaHandlerInstrumentation.class.getName() + "$RxExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class RxExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // Capture current context before RxJava chain starts
      io.opentelemetry.context.Context current = io.opentelemetry.context.Context.current();
      // Store in thread local for SQL instrumentation to pick up
      ContextHolder.set(current);
    }
  }
}
