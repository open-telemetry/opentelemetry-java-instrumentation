/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setSqlConnectOptions;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataCapture;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import java.util.ArrayList;
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
        @Advice.FieldValue("connectHandler") @Nullable Handler<SqlConnection> connectHandler) {
      List<SqlConnectOptions> databases =
          VertxSqlClientSingletons.getBuilderDatabases(clientBuilder);
      if (databases != null && !databases.isEmpty()) {
        List<SqlConnectOptions> snapshots = new ArrayList<>(databases.size());
        for (SqlConnectOptions database : databases) {
          snapshots.add(database != null ? new SqlConnectOptions(database) : null);
        }
        databases = snapshots;
        SqlConnectOptions firstDatabase = databases.get(0);
        VertxSqlAddressGroup addressGroup = VertxSqlAddressGroup.of(databases);
        setSqlConnectOptions(firstDatabase);
        setAddressGroup(addressGroup);
        return new Object[] {
          VertxSqlClientSingletons.wrapConnectHandler(connectHandler, firstDatabase, addressGroup),
          new BuildState(databases, null, connectHandler)
        };
      }

      VertxSqlClientDataCapture dataCapture = new VertxSqlClientDataCapture();
      VertxSqlClientSingletons.setBuildingDataCapture(dataCapture);
      return new Object[] {connectHandler, new BuildState(null, dataCapture, connectHandler)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToFields(@ToField(value = "connectHandler", index = 0))
    public static Object[] onExit(
        @Advice.Return @Nullable Object client,
        @Advice.FieldValue("connectHandler") @Nullable Handler<SqlConnection> connectHandler,
        @Advice.Enter @Nullable Object[] enterState) {
      setSqlConnectOptions(null);
      setAddressGroup(null);
      VertxSqlClientSingletons.setBuildingDataCapture(null);

      if (enterState == null) {
        return new Object[] {connectHandler};
      }

      BuildState state = (BuildState) enterState[1];
      if (client instanceof Pool) {
        Pool pool = (Pool) client;
        if (state.databases != null) {
          SqlConnectOptions firstDatabase = state.databases.get(0);
          setPoolConnectOptions(pool, firstDatabase);
          setPoolAddressGroup(pool, VertxSqlAddressGroup.of(state.databases));
          VertxSqlClientSingletons.resolveAndStoreDbSystem(pool, firstDatabase);
        } else {
          VertxSqlClientSingletons.setPoolDataCapture(pool, state.dataCapture);
        }
      }
      return new Object[] {state.connectHandler};
    }

    public static class BuildState {
      @Nullable public final List<SqlConnectOptions> databases;
      @Nullable public final VertxSqlClientDataCapture dataCapture;
      @Nullable public final Handler<SqlConnection> connectHandler;

      public BuildState(
          @Nullable List<SqlConnectOptions> databases,
          @Nullable VertxSqlClientDataCapture dataCapture,
          @Nullable Handler<SqlConnection> connectHandler) {
        this.databases = databases;
        this.dataCapture = dataCapture;
        this.connectHandler = connectHandler;
      }
    }
  }
}
