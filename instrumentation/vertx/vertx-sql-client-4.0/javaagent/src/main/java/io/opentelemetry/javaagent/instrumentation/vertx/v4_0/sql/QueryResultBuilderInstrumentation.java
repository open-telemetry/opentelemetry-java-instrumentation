/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.endQuerySpan;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class QueryResultBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.QueryResultBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("tryComplete"),
        QueryResultBuilderInstrumentation.class.getName() + "$CompleteAdvice");
    transformer.applyAdviceToMethod(
        named("tryFail").and(takesArguments(Throwable.class)),
        QueryResultBuilderInstrumentation.class.getName() + "$FailAdvice");
  }

  @SuppressWarnings("unused")
  public static class CompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(@Advice.FieldValue("context") Context vertxContext) {
      ContextInternal contextInternal = (ContextInternal) vertxContext;
      return endQuerySpan(contextInternal.localContextData(), null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FailAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.Argument(0) Throwable throwable,
        @Advice.FieldValue("context") Context vertxContext) {
      ContextInternal contextInternal = (ContextInternal) vertxContext;
      return endQuerySpan(contextInternal.localContextData(), throwable);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
