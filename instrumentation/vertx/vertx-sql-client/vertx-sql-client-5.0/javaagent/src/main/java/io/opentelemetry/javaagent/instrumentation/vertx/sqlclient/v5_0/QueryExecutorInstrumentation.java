/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDeferredRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.core.Promise;
import io.vertx.core.internal.PromiseInternal;
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
      VertxSqlClientSingletons.captureQueryExecutorInfo(queryExecutor);
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    public static class AdviceScope {
      private final CallDepth callDepth;
      @Nullable private Promise<?> promise;
      @Nullable private Scope scope;

      private AdviceScope(CallDepth callDepth) {
        this.callDepth = callDepth;
      }

      public static AdviceScope start(Object queryExecutor, String methodName, Object[] arguments) {
        CallDepth callDepth = CallDepth.forClass(queryExecutor.getClass());
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth);
        }
        AdviceScope adviceScope = new AdviceScope(callDepth);
        Context parentContext = Context.current();
        if (parentContext.get(QUERY_STATE) != null) {
          adviceScope.scope = parentContext.with(QUERY_STATE, null).makeCurrent();
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
          return adviceScope;
        }

        VertxSqlClientInfo info = VertxSqlClientSingletons.getQueryExecutorInfo(queryExecutor);
        if (info == null) {
          return adviceScope;
        }

        VertxSqlClientRequest otelRequest =
            VertxSqlClientSingletons.isSupplierQuery(queryExecutor)
                ? new VertxSqlClientDeferredRequest(sql, info, parameterizedQuery, batchSize)
                : new VertxSqlClientRequest(sql, info, parameterizedQuery, batchSize);
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          return adviceScope;
        }

        Context context = instrumenter().start(parentContext, otelRequest);
        adviceScope.promise = promiseInternal;
        VertxSqlClientUtil.attachRequest(promiseInternal, otelRequest, context, parentContext);
        if (otelRequest instanceof VertxSqlClientDeferredRequest) {
          context =
              context.with(
                  QUERY_STATE,
                  new VertxSqlClientQueryState(
                      (VertxSqlClientDeferredRequest) otelRequest, promiseInternal, context));
        } else if (context.get(QUERY_STATE) != null) {
          context = context.with(QUERY_STATE, null);
        }
        if (adviceScope.scope != null) {
          adviceScope.scope.close();
        }
        adviceScope.scope = context.makeCurrent();
        return adviceScope;
      }

      private void endSpan(@Nullable Throwable throwable) {
        if (promise == null) {
          return;
        }
        Scope parentScope = VertxSqlClientUtil.endQuerySpan(instrumenter(), promise, throwable);
        if (parentScope != null) {
          parentScope.close();
        }
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }

        Scope executionScope = scope;
        scope = null;
        if (executionScope != null) {
          executionScope.close();
        }
        if (throwable != null) {
          endSpan(throwable);
        }
      }

      private void endSpan(@Nullable Throwable throwable) {
        if (promise == null) {
          return;
        }
        VertxSqlClientUtil.endQuerySpanAndGetParentContext(instrumenter(), promise, throwable);
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
