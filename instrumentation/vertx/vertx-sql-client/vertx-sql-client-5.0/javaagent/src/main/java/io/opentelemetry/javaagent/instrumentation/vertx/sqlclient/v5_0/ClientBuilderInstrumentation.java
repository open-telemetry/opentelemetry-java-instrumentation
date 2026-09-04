/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolClientInfoProvider;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
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
      VertxSqlClientSingletons.storeBuilderDatabases(
          clientBuilder, singletonList(sqlConnectOptions));
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectingToSupplierAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Object clientBuilder) {
      VertxSqlClientSingletons.storeBuilderDatabases(clientBuilder, null);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectingToListAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object clientBuilder, @Advice.Argument(0) List<SqlConnectOptions> databases) {
      VertxSqlClientSingletons.storeBuilderDatabases(clientBuilder, databases);
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
      if (databases != null && !databases.isEmpty()) {
        VertxSqlClientInfo info =
            VertxSqlClientInfo.create(
                databases, VertxSqlClientUtil.getDbSystemNameFromClassName(driver));
        setClientInfoProvider(info);
        return new Object[] {
          info != null
              ? VertxSqlClientSingletons.wrapConnectHandler(connectHandler, info)
              : connectHandler,
          new BuildState(info, null, connectHandler)
        };
      }

      VertxSqlClientInfoCapture supplierCapture = new VertxSqlClientInfoCapture();
      setClientInfoProvider(supplierCapture);
      VertxSqlClientSingletons.setBuildingSupplierCapture(supplierCapture);
      return new Object[] {connectHandler, new BuildState(null, supplierCapture, connectHandler)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToFields(@ToField(value = "connectHandler", index = 0))
    public static Object[] onExit(
        @Advice.Return @Nullable Object client,
        @Advice.FieldValue("connectHandler") @Nullable Handler<SqlConnection> connectHandler,
        @Advice.Enter @Nullable Object[] enterState) {
      setClientInfoProvider(null);
      VertxSqlClientSingletons.setBuildingSupplierCapture(null);

      if (enterState == null) {
        return new Object[] {connectHandler};
      }

      BuildState state = (BuildState) enterState[1];
      if (client instanceof Pool) {
        Pool pool = (Pool) client;
        if (state.info != null) {
          setPoolClientInfoProvider(pool, state.info);
        } else {
          VertxSqlClientSingletons.setPoolSupplierCapture(pool, state.supplierCapture);
        }
      }
      return new Object[] {state.connectHandler};
    }

    public static class BuildState {
      @Nullable public final VertxSqlClientInfo info;
      @Nullable public final VertxSqlClientInfoCapture supplierCapture;
      @Nullable public final Handler<SqlConnection> connectHandler;

      public BuildState(
          @Nullable VertxSqlClientInfo info,
          @Nullable VertxSqlClientInfoCapture supplierCapture,
          @Nullable Handler<SqlConnection> connectHandler) {
        this.info = info;
        this.supplierCapture = supplierCapture;
        this.connectHandler = connectHandler;
      }
    }
  }
}
