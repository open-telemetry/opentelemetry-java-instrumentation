/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.getSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.sqlclient.impl.PreparedStatement;
import io.vertx.sqlclient.impl.QueryExecutorUtil;
import javax.annotation.Nullable;
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

    public static class AdviceScope {
      private final CallDepth callDepth;
      @Nullable private final VertxSqlClientRequest otelRequest;
      @Nullable private final Context context;
      @Nullable private final Scope scope;

      private AdviceScope(
          CallDepth callDepth,
          @Nullable VertxSqlClientRequest otelRequest,
          @Nullable Context context,
          @Nullable Scope scope) {
        this.callDepth = callDepth;
        this.otelRequest = otelRequest;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(Object queryExecutor, Object[] arguments) {
        CallDepth callDepth = CallDepth.forClass(queryExecutor.getClass());
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
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
            promiseInternal = (PromiseInternal<?>) argument;
          }
        }
        if (sql == null || promiseInternal == null) {
          return new AdviceScope(callDepth, null, null, null);
        }

        VertxSqlClientRequest otelRequest =
            new VertxSqlClientRequest(sql, QueryExecutorUtil.getConnectOptions(queryExecutor));
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          return new AdviceScope(callDepth, otelRequest, null, null);
        }

        Context context = instrumenter().start(parentContext, otelRequest);
        VertxSqlClientUtil.attachRequest(promiseInternal, otelRequest, context, parentContext);
        return new AdviceScope(callDepth, otelRequest, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (scope == null || context == null || otelRequest == null) {
          return;
        }

        scope.close();
        if (throwable != null) {
          instrumenter().end(context, otelRequest, null, throwable);
        }
        // span will be ended in QueryResultBuilderInstrumentation
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This Object queryExecutor, @Advice.AllArguments Object[] arguments) {
      return AdviceScope.start(queryExecutor, arguments);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
