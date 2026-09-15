/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.QueryInfoScope;
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
    public static QueryInfoScope onEnter(@Advice.This PreparedStatement preparedStatement) {
      return VertxSqlClientSingletons.enterQueryInfo(
          VertxSqlClientSingletons.getPreparedStatementInfo(preparedStatement), null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable QueryInfoScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
