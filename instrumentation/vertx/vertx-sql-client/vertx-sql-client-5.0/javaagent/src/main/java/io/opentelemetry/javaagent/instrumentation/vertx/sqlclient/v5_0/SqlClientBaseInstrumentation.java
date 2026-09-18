/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentClientInfo;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentConstructionState;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentQuerySupplier;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
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
      VertxSqlClientConstructionState state = currentConstructionState().get();
      if (state != null) {
        state.attachClient(sqlClientBase);
      } else {
        VertxSqlClientSingletons.attachClientInfo(sqlClientBase, currentClientInfo().get());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static QueryAdviceState onEnter(@Advice.This SqlClientBase sqlClientBase) {
      CallDepth callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.getAndIncrement() > 0) {
        return new QueryAdviceState(callDepth, null, null);
      }

      VertxSqlClientInfo previousInfo =
          currentClientInfo().set(VertxSqlClientSingletons.getClientInfo(sqlClientBase));
      VertxSqlClientSupplierInfo previousSupplier =
          currentQuerySupplier().set(VertxSqlClientSingletons.getClientSupplier(sqlClientBase));
      return new QueryAdviceState(callDepth, previousInfo, previousSupplier);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable QueryAdviceState state) {
      if (state == null || state.callDepth.decrementAndGet() > 0) {
        return;
      }
      try {
        currentQuerySupplier().restore(state.previousSupplier);
      } finally {
        currentClientInfo().restore(state.previousInfo);
      }
    }

    public static final class QueryAdviceState {
      public final CallDepth callDepth;
      @Nullable public final VertxSqlClientInfo previousInfo;
      @Nullable public final VertxSqlClientSupplierInfo previousSupplier;

      public QueryAdviceState(
          CallDepth callDepth,
          @Nullable VertxSqlClientInfo previousInfo,
          @Nullable VertxSqlClientSupplierInfo previousSupplier) {
        this.callDepth = callDepth;
        this.previousInfo = previousInfo;
        this.previousSupplier = previousSupplier;
      }
    }
  }
}
