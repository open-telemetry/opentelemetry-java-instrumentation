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
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
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
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static BuildState onEnter(@Advice.This Object clientBuilder) {
      List<SqlConnectOptions> databases =
          VertxSqlClientSingletons.getBuilderDatabases(clientBuilder);
      if (databases != null && !databases.isEmpty()) {
        databases = new ArrayList<>(databases);
        setSqlConnectOptions(databases.get(0));
        setAddressGroup(VertxSqlAddressGroup.of(databases));
        return new BuildState(databases, null);
      }

      VertxSqlClientDataCapture dataCapture = new VertxSqlClientDataCapture();
      VertxSqlClientSingletons.setBuildingDataCapture(dataCapture);
      return new BuildState(null, dataCapture);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable Object client, @Advice.Enter @Nullable BuildState state) {
      setSqlConnectOptions(null);
      setAddressGroup(null);
      VertxSqlClientSingletons.setBuildingDataCapture(null);

      if (state == null || !(client instanceof Pool)) {
        return;
      }
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

    public static class BuildState {
      @Nullable public final List<SqlConnectOptions> databases;
      @Nullable public final VertxSqlClientDataCapture dataCapture;

      public BuildState(
          @Nullable List<SqlConnectOptions> databases,
          @Nullable VertxSqlClientDataCapture dataCapture) {
        this.databases = databases;
        this.dataCapture = dataCapture;
      }
    }
  }
}
