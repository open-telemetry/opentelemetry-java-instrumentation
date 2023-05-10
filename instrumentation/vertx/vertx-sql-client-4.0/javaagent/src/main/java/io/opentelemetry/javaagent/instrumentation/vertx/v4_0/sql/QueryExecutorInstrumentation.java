/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_PARENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.OTEL_REQUEST_KEY;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.getSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.sqlclient.impl.PreparedStatement;
import io.vertx.sqlclient.impl.QueryExecutorUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class QueryExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.QueryExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), QueryExecutorInstrumentation.class.getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("executeSimpleQuery", "executeExtendedQuery", "executeBatchQuery"),
        QueryExecutorInstrumentation.class.getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Object queryExecutor) {
      // copy connection options from ThreadLocal to VirtualField
      QueryExecutorUtil.setConnectOptions(queryExecutor, getSqlConnectOptions());
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object queryExecutor,
        @Advice.AllArguments Object[] arguments,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") VertxSqlClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(queryExecutor.getClass());
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      // The parameter we need are in different positions, we are not going to have separate
      // advices for all of them. The method gets the statement either as String or
      // PreparedStatement, use the first argument that is either of these. PromiseInternal is
      // always at the end of the argument list.
      String sql = null;
      PromiseInternal<?> promiseInternal = null;
      for (Object argument : arguments) {
        if (sql == null) {
          if (argument instanceof String) {
            sql = (String) argument;
          } else if (argument instanceof PreparedStatement) {
            sql = ((PreparedStatement) argument).sql();
          }
        } else if (argument instanceof PromiseInternal) {
          promiseInternal = (PromiseInternal) argument;
        }
      }
      if (sql == null || promiseInternal == null) {
        return;
      }

      otelRequest =
          new VertxSqlClientRequest(sql, QueryExecutorUtil.getConnectOptions(queryExecutor));
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
      promiseInternal.context().localContextData().put(OTEL_REQUEST_KEY, otelRequest);
      promiseInternal.context().localContextData().put(OTEL_CONTEXT_KEY, context);
      promiseInternal.context().localContextData().put(OTEL_PARENT_CONTEXT_KEY, parentContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") VertxSqlClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      }
      // span will be ended in QueryResultBuilderInstrumentation
    }
  }
}
