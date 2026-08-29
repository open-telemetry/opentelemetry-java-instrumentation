/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getClientDataProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getDbSystem;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientData;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataProvider;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.internal.PreparedStatement;
import java.util.Collection;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class QueryExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.QueryExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("executeSimpleQuery", "executeExtendedQuery", "executeBatchQuery"),
        getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Object queryExecutor) {
      VertxSqlClientDataProvider dataProvider = getClientDataProvider();
      if (dataProvider == null) {
        dataProvider =
            new VertxSqlClientData(
                getSqlConnectOptions(), getDbSystem(), VertxSqlClientUtil.getAddressGroup());
      }
      VertxSqlClientUtil.setQueryExecutorData(queryExecutor, dataProvider);
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    public static class AdviceScope implements VertxSqlClientDataCapture.Listener {
      private final CallDepth callDepth;
      @Nullable private final String sql;
      private final boolean parameterizedQuery;
      @Nullable private final PromiseInternal<?> promiseInternal;
      @Nullable private final Long batchSize;
      @Nullable private final Context parentContext;
      @Nullable private VertxSqlClientRequest otelRequest;
      @Nullable private Context context;
      @Nullable private Scope scope;
      private boolean exited;
      private boolean cancelled;

      private AdviceScope(CallDepth callDepth) {
        this(callDepth, null, false, null, null, null);
      }

      private AdviceScope(
          CallDepth callDepth,
          String sql,
          boolean parameterizedQuery,
          PromiseInternal<?> promiseInternal,
          @Nullable Long batchSize,
          Context parentContext) {
        this.callDepth = callDepth;
        this.sql = sql;
        this.parameterizedQuery = parameterizedQuery;
        this.promiseInternal = promiseInternal;
        this.batchSize = batchSize;
        this.parentContext = parentContext;
      }

      public static AdviceScope start(Object queryExecutor, String methodName, Object[] arguments) {
        CallDepth callDepth = CallDepth.forClass(queryExecutor.getClass());
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth);
        }

        // The parameter we need are in different positions, we are not going to have separate
        // advices for all of them. The method gets the query either as String or
        // PreparedStatement, use the first argument that is either of these. PromiseInternal is
        // always at the end of the argument list.
        String sql = null;
        boolean parameterizedQuery = !methodName.equals("executeSimpleQuery");
        PromiseInternal<?> promiseInternal = null;
        Long batchSize = null;
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
          if (methodName.equals("executeBatchQuery") && argument instanceof Collection) {
            int size = ((Collection<?>) argument).size();
            batchSize = size == 1 ? null : (long) size;
          }
        }
        if (sql == null || promiseInternal == null) {
          return new AdviceScope(callDepth);
        }

        VertxSqlClientDataProvider dataProvider =
            VertxSqlClientUtil.getQueryExecutorDataProvider(queryExecutor);
        if (dataProvider == null) {
          return new AdviceScope(callDepth);
        }
        AdviceScope adviceScope =
            new AdviceScope(
                callDepth, sql, parameterizedQuery, promiseInternal, batchSize, Context.current());
        VertxSqlClientData data = dataProvider.get();
        if (data == null) {
          if (dataProvider instanceof VertxSqlClientDataCapture) {
            VertxSqlClientSingletons.setCaptureListener(adviceScope);
          }
          return adviceScope;
        }
        adviceScope.startSpan(data);
        return adviceScope;
      }

      private synchronized void startSpan(VertxSqlClientData data) {
        if (cancelled
            || context != null
            || sql == null
            || promiseInternal == null
            || parentContext == null) {
          return;
        }
        SqlConnectOptions connectOptions = data.getConnectOptions();
        if (connectOptions == null) {
          return;
        }
        String dbSystem = data.getDbSystem();
        if (dbSystem == null) {
          dbSystem = VertxSqlClientSingletons.getConnectOptionsDbSystem(connectOptions);
        }
        if (dbSystem == null) {
          dbSystem = VertxSqlClientUtil.getDbSystemNameFromClassName(connectOptions);
        }
        VertxSqlClientRequest otelRequest =
            new VertxSqlClientRequest(
                sql,
                connectOptions,
                parameterizedQuery,
                dbSystem,
                batchSize,
                data.getAddressGroup());
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          cancelled = true;
          return;
        }

        this.otelRequest = otelRequest;
        context = instrumenter().start(parentContext, otelRequest);
        VertxSqlClientUtil.attachRequest(promiseInternal, otelRequest, context, parentContext);
        if (!exited) {
          scope = context.makeCurrent();
        }
      }

      @Override
      public void onCapture(VertxSqlClientData data) {
        startSpan(data);
      }

      public synchronized void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        VertxSqlClientSingletons.setCaptureListener(null);
        exited = true;
        if (scope != null) {
          scope.close();
        }
        if (throwable != null) {
          cancelled = true;
          if (context != null && otelRequest != null) {
            instrumenter().end(context, otelRequest, null, throwable);
          }
        }
        // span will be ended in QueryResultBuilderInstrumentation
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Object queryExecutor,
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] arguments) {
      return AdviceScope.start(queryExecutor, methodName, arguments);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
