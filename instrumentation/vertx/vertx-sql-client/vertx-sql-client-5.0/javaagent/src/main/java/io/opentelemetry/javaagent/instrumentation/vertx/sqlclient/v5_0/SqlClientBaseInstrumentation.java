/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.QueryInfoScope;
import io.vertx.sqlclient.internal.SqlClientBase;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlClientBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.internal.SqlClientBase");
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
    public static void onExit(@Advice.This SqlClientBase sqlClientBase) {
      VertxSqlClientConstructionState state = VertxSqlClientSingletons.getConstructionState();
      if (state != null) {
        state.attachClient(sqlClientBase);
      } else {
        VertxSqlClientSingletons.attachClientInfo(
            sqlClientBase, VertxSqlClientSingletons.getClientInfo());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static QueryAdviceState onEnter(@Advice.This SqlClientBase sqlClientBase) {
      CallDepth callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.getAndIncrement() > 0) {
        return new QueryAdviceState(callDepth, null);
      }

      QueryInfoScope scope =
          VertxSqlClientSingletons.enterQueryInfo(
              VertxSqlClientSingletons.getClientInfo(sqlClientBase),
              VertxSqlClientSingletons.getClientSupplier(sqlClientBase));
      return new QueryAdviceState(callDepth, scope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable QueryAdviceState state) {
      if (state != null) {
        state.close();
      }
    }

    public static final class QueryAdviceState {
      private final CallDepth callDepth;
      @Nullable private final QueryInfoScope scope;

      public QueryAdviceState(CallDepth callDepth, @Nullable QueryInfoScope scope) {
        this.callDepth = callDepth;
        this.scope = scope;
      }

      public void close() {
        if (callDepth.decrementAndGet() == 0 && scope != null) {
          scope.close();
        }
      }
    }
  }
}
