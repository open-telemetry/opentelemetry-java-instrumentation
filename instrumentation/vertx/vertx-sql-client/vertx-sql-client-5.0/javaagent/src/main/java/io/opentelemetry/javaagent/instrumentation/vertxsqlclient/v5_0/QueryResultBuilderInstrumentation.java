/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertxsqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.endQuerySpan;
import static io.opentelemetry.javaagent.instrumentation.vertxsqlclient.v5_0.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Promise;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class QueryResultBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.QueryResultBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("complete"), getClass().getName() + "$CompleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class CompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter(
        @Advice.Argument(1) Throwable throwable, @Advice.FieldValue("handler") Promise<?> promise) {
      return endQuerySpan(instrumenter(), promise, throwable);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
