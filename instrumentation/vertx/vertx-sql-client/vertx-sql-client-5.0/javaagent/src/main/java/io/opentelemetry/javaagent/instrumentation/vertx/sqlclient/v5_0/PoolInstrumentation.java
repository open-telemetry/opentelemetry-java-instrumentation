/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getDbSystemNameFromClassName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.resolveDbSystemName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.sqlclient.Pool");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.sqlclient.Pool"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pool")
            .and(isStatic())
            .and(takesArguments(3))
            .and(takesArgument(1, named("io.vertx.sqlclient.SqlConnectOptions")))
            .and(returns(named("io.vertx.sqlclient.Pool"))),
        getClass().getName() + "$PoolAdvice");

    transformer.applyAdviceToMethod(
        named("getConnection").and(takesNoArguments()).and(returns(named("io.vertx.core.Future"))),
        getClass().getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class PoolAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static PoolConstructionState onEnter(
        @Advice.Argument(1) SqlConnectOptions sqlConnectOptions,
        @Advice.Origin("#t") String declaringTypeName) {
      CallDepth callDepth = CallDepth.forClass(Pool.class);
      if (callDepth.getAndIncrement() > 0) {
        return new PoolConstructionState(callDepth, null);
      }

      String dbSystemName = resolveDbSystemName(sqlConnectOptions, declaringTypeName);
      VertxSqlClientConstructionState constructionState =
          new VertxSqlClientConstructionState(singletonList(sqlConnectOptions), dbSystemName);
      VertxSqlClientSingletons.setConstructionState(constructionState);
      return new PoolConstructionState(callDepth, constructionState);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable Pool pool, @Advice.Enter PoolConstructionState state) {
      if (state.isNested()) {
        return;
      }

      VertxSqlClientConstructionState constructionState = state.getConstructionState();
      VertxSqlClientSingletons.setConstructionState(null);
      if (constructionState != null) {
        if (pool != null) {
          constructionState.setDbSystemName(getDbSystemNameFromClassName(pool));
        }
        constructionState.complete(pool);
      }
    }

    public static final class PoolConstructionState {
      private final CallDepth callDepth;
      @Nullable private final VertxSqlClientConstructionState constructionState;

      public PoolConstructionState(
          CallDepth callDepth, @Nullable VertxSqlClientConstructionState constructionState) {
        this.callDepth = callDepth;
        this.constructionState = constructionState;
      }

      public boolean isNested() {
        return callDepth.decrementAndGet() > 0;
      }

      @Nullable
      public VertxSqlClientConstructionState getConstructionState() {
        return constructionState;
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Future<SqlConnection> onExit(
        @Advice.This Pool pool, @Advice.Return Future<SqlConnection> future) {
      VertxSqlClientInfo info = VertxSqlClientSingletons.getPoolClientInfo(pool);
      return wrapContext(VertxSqlClientSingletons.attachClientInfo(future, info));
    }
  }
}
