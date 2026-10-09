/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0.VertxSqlClientSingletons.currentClientInfoReference;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.sqlclient.impl.SqlClientBase;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlClientBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.SqlClientBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("query", "preparedQuery"), getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This SqlClientBase<?> sqlClientBase) {
      VertxSqlClientSingletons.attachClientInfoReference(
          sqlClientBase, currentClientInfoReference().get());
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static VertxSqlClientInfoReference onEnter(@Advice.This SqlClientBase<?> sqlClientBase) {
      CallDepth callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.getAndIncrement() > 0) {
        return currentClientInfoReference().get();
      }

      return currentClientInfoReference()
          .set(VertxSqlClientSingletons.getClientInfoReference(sqlClientBase));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable VertxSqlClientInfoReference previous) {
      CallDepth callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      currentClientInfoReference().restore(previous);
    }
  }
}
