/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_PARENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_REQUEST_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.instrumenter;
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
      VertxSqlClientRequest otelRequest =
          (VertxSqlClientRequest) contextInternal.localContextData().get(OTEL_REQUEST_KEY);
      io.opentelemetry.context.Context otelContext =
          (io.opentelemetry.context.Context)
              contextInternal.localContextData().get(OTEL_CONTEXT_KEY);
      io.opentelemetry.context.Context otelParentContext =
          (io.opentelemetry.context.Context)
              contextInternal.localContextData().get(OTEL_PARENT_CONTEXT_KEY);
      if (otelRequest == null || otelContext == null || otelParentContext == null) {
        return null;
      }
      instrumenter().end(otelContext, otelRequest, null, null);
      return otelParentContext.makeCurrent();
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
      VertxSqlClientRequest otelRequest =
          (VertxSqlClientRequest) contextInternal.localContextData().get(OTEL_REQUEST_KEY);
      io.opentelemetry.context.Context otelContext =
          (io.opentelemetry.context.Context)
              contextInternal.localContextData().get(OTEL_CONTEXT_KEY);
      io.opentelemetry.context.Context otelParentContext =
          (io.opentelemetry.context.Context)
              contextInternal.localContextData().get(OTEL_PARENT_CONTEXT_KEY);
      if (otelRequest == null || otelContext == null || otelParentContext == null) {
        return null;
      }
      instrumenter().end(otelContext, otelRequest, null, throwable);
      return otelParentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
