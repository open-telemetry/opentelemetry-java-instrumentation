/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.sql;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Completable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TransactionImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.TransactionImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("wrap").and(returns(named("io.vertx.core.Completable"))),
        TransactionImplInstrumentation.class.getName() + "$WrapHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapHandlerAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void wrapHandler(@Advice.Return(readOnly = false) Completable<?> handler) {
      handler = CompletableWrapper.wrap(handler);
    }
  }
}
