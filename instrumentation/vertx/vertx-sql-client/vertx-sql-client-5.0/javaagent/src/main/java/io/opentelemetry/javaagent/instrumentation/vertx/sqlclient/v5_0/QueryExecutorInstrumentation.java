/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDeferredRequest;
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
    transformer.applyAdviceToMethod(
        namedOneOf("executeSimpleQuery", "executeExtendedQuery", "executeBatchQuery"),
        getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    public static class AdviceScope {
      private final CallDepth callDepth;
      @Nullable private final Promise<?> promise;
      @Nullable private final Scope scope;

      private AdviceScope(CallDepth callDepth) {
        this.callDepth = callDepth;
        this.promise = null;
        this.scope = null;
      }

      private AdviceScope(CallDepth callDepth, @Nullable Promise<?> promise, Context context) {
        this.callDepth = callDepth;
        this.promise = promise;
        this.scope = context.makeCurrent();
      }

      public static AdviceScope start(
          Object queryExecutor,
          Object scheduler,
          @Nullable Object cursorId,
          String methodName,
          Object[] arguments)
          throws Throwable {
        CallDepth callDepth = CallDepth.forClass(queryExecutor.getClass());
        int previousCallDepth = callDepth.getAndIncrement();
        try {
          if (previousCallDepth > 0) {
            return new AdviceScope(callDepth);
          }
          return start(callDepth, scheduler, cursorId, methodName, arguments);
        } catch (Throwable t) {
          callDepth.decrementAndGet();
          throw t;
        }
      }

      private static AdviceScope start(
          CallDepth callDepth,
          Object scheduler,
          @Nullable Object cursorId,
          String methodName,
          Object[] arguments) {
        Context parentContext = Context.current();
        Context context =
            parentContext.get(QUERY_STATE) != null
                ? parentContext.with(QUERY_STATE, null)
                : parentContext;
        // Cursor fetches are not traced as separate query executions.
        if (cursorId != null) {
          return new AdviceScope(callDepth, null, context);
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
          return new AdviceScope(callDepth, null, context);
        }

        VertxSqlClientState state = VertxSqlClientSingletons.getClientState(scheduler);
        if (state == null) {
          return new AdviceScope(callDepth, null, context);
        }

        VertxSqlClientRequest otelRequest =
            state.isSupplier()
                ? new VertxSqlClientDeferredRequest(
                    sql, state.getInfo(), parameterizedQuery, batchSize)
                : new VertxSqlClientRequest(sql, state.getInfo(), parameterizedQuery, batchSize);
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          return new AdviceScope(callDepth, null, context);
        }

        context = instrumenter().start(context, otelRequest);
        VertxSqlClientUtil.attachRequest(promiseInternal, otelRequest, context, parentContext);
        if (otelRequest instanceof VertxSqlClientDeferredRequest) {
          context =
              context.with(
                  QUERY_STATE,
                  new VertxSqlClientQueryState(
                      (VertxSqlClientDeferredRequest) otelRequest, promiseInternal, context));
        }
        return new AdviceScope(callDepth, promiseInternal, context);
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }

        if (scope != null) {
          scope.close();
        }
        if (throwable != null && promise != null) {
          VertxSqlClientUtil.endQuerySpanAndGetParentContext(instrumenter(), promise, throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Object queryExecutor,
        @Advice.Argument(0) Object scheduler,
        @Advice.Argument(value = 6, optional = true) @Nullable Object cursorId,
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] arguments)
        throws Throwable {
      return AdviceScope.start(queryExecutor, scheduler, cursorId, methodName, arguments);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
