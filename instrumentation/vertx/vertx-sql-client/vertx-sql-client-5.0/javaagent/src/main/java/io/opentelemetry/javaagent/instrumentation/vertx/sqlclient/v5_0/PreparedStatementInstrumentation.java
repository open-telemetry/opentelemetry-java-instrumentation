/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentClientInfo;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentQuerySupplier;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.vertx.sqlclient.PreparedStatement;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PreparedStatementInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.PreparedStatementBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("query"), getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static QueryAdviceState onEnter(@Advice.This PreparedStatement preparedStatement) {
      VertxSqlClientInfo previousInfo =
          currentClientInfo()
              .set(VertxSqlClientSingletons.getPreparedStatementInfo(preparedStatement));
      VertxSqlClientSupplierInfo previousSupplier = currentQuerySupplier().set(null);
      return new QueryAdviceState(previousInfo, previousSupplier);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable QueryAdviceState state) {
      if (state == null) {
        return;
      }
      currentQuerySupplier().restore(state.previousSupplier);
      currentClientInfo().restore(state.previousInfo);
    }

    public static final class QueryAdviceState {
      @Nullable public final VertxSqlClientInfo previousInfo;
      @Nullable public final VertxSqlClientSupplierInfo previousSupplier;

      public QueryAdviceState(
          @Nullable VertxSqlClientInfo previousInfo,
          @Nullable VertxSqlClientSupplierInfo previousSupplier) {
        this.previousInfo = previousInfo;
        this.previousSupplier = previousSupplier;
      }
    }
  }
}
