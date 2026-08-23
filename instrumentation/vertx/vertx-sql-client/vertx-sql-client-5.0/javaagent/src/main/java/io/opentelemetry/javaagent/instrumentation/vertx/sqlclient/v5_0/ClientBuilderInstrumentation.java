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
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Captures the servers a client is built with when {@link
 * io.vertx.sqlclient.ClientBuilder#connectingTo(List)} is used. The other {@code connectingTo}
 * overloads name a single server, and they all funnel into {@code connectingTo(Supplier)}, which is
 * where the servers of an earlier call are dropped.
 */
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
    // runs after the delegation to connectingTo(Supplier) cleared the previous servers
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
      // every connectingTo overload ends up here, so this is where the previously configured
      // servers stop being what the builder connects to
      VertxSqlClientSingletons.storeBuilderDatabases(clientBuilder, null);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectingToListAdvice {
    // runs after the delegation to connectingTo(Supplier) cleared the previous servers
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object clientBuilder, @Advice.Argument(0) List<SqlConnectOptions> databases) {
      VertxSqlClientSingletons.storeBuilderDatabases(clientBuilder, databases);
    }
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static List<SqlConnectOptions> onEnter(@Advice.This Object clientBuilder) {
      List<SqlConnectOptions> databases =
          VertxSqlClientSingletons.getBuilderDatabases(clientBuilder);
      if (databases == null || databases.isEmpty()) {
        return null;
      }

      // set the client state to ThreadLocal, it will be read in SqlClientBase constructor
      setSqlConnectOptions(databases.get(0));
      setAddressGroup(VertxSqlAddressGroup.of(databases));
      return databases;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable Object client,
        @Advice.Enter @Nullable List<SqlConnectOptions> databases) {
      if (databases == null) {
        return;
      }

      setSqlConnectOptions(null);
      setAddressGroup(null);

      SqlConnectOptions firstDatabase = databases.get(0);
      if (client instanceof Pool) {
        Pool pool = (Pool) client;
        setPoolConnectOptions(pool, firstDatabase);
        setPoolAddressGroup(pool, VertxSqlAddressGroup.of(databases));
        VertxSqlClientSingletons.resolveAndStoreDbSystem(pool, firstDatabase);
      }
    }
  }
}
