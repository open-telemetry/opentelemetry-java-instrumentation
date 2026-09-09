/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.core.Handler;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// All connectingTo overloads delegate to the Supplier overload, which clears the previous target.
class ClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.ClientBuilderBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("connectingTo")
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.util.function.Supplier"))),
        getClass().getName() + "$ConnectingToSupplierAdvice");

    transformer.applyAdviceToMethod(
        named("connectingTo")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.sqlclient.SqlConnectOptions"))),
        getClass().getName() + "$ConnectingToOptionsAdvice");

    transformer.applyAdviceToMethod(
        named("connectingTo").and(takesArguments(1)).and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$ConnectingToListAdvice");

    transformer.applyAdviceToMethod(
        named("build").and(takesNoArguments()), getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectingToOptionsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object clientBuilder,
        @Advice.Argument(0) SqlConnectOptions sqlConnectOptions) {
      VertxSqlClientSingletons.setBuilderDatabases(clientBuilder, singletonList(sqlConnectOptions));
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectingToSupplierAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Object clientBuilder) {
      VertxSqlClientSingletons.setBuilderDatabases(clientBuilder, null);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectingToListAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object clientBuilder, @Advice.Argument(0) List<SqlConnectOptions> databases) {
      VertxSqlClientSingletons.setBuilderDatabases(clientBuilder, databases);
    }
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {
    // The returned array contains the handler to install at index 0 and the exit state at index 1.
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToFields(@ToField(value = "connectHandler", index = 0))
    public static Object[] onEnter(
        @Advice.This Object clientBuilder,
        @Advice.FieldValue("driver") Object driver,
        @Advice.FieldValue("connectHandler") @Nullable Handler<SqlConnection> connectHandler) {
      List<SqlConnectOptions> databases =
          VertxSqlClientSingletons.getBuilderDatabases(clientBuilder);
      VertxSqlClientConstructionState state =
          new VertxSqlClientConstructionState(
              databases != null && !databases.isEmpty() ? databases : null,
              VertxSqlClientUtil.getDbSystemNameFromClassName(driver));
      VertxSqlClientSingletons.setConstructionState(state);
      VertxSqlClientInfo info = state.getInfo();
      return new Object[] {
        state.getSupplier() == null && info != null
            ? VertxSqlClientSingletons.wrapConnectHandler(connectHandler, info)
            : connectHandler,
        new BuildState(state, connectHandler)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToFields(@ToField(value = "connectHandler", index = 0))
    public static Object[] onExit(
        @Advice.Return @Nullable Object client,
        @Advice.FieldValue("connectHandler") @Nullable Handler<SqlConnection> connectHandler,
        @Advice.Enter @Nullable Object[] enterState) {
      VertxSqlClientSingletons.setConstructionState(null);
      if (enterState == null) {
        return new Object[] {connectHandler};
      }

      BuildState state = (BuildState) enterState[1];
      state.constructionState.complete(client);
      // Restore the original connect handler after onEnter temporarily replaced it.
      return new Object[] {state.connectHandler};
    }

    public static class BuildState {
      public final VertxSqlClientConstructionState constructionState;
      @Nullable public final Handler<SqlConnection> connectHandler;

      public BuildState(
          VertxSqlClientConstructionState constructionState,
          @Nullable Handler<SqlConnection> connectHandler) {
        this.constructionState = constructionState;
        this.connectHandler = connectHandler;
      }
    }
  }
}
