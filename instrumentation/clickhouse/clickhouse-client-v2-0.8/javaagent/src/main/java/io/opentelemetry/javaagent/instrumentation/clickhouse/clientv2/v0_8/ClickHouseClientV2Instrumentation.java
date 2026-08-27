/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8.ClickHouseClientV2Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.Client;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseScope;
import io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8.ClickHouseClientV2Singletons.ServerInfo;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ClickHouseClientV2Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("query"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, isSubTypeOf(Map.class)))
            .and(takesArgument(2, named("com.clickhouse.client.api.query.QuerySettings"))),
        getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Client client) {
      ClickHouseClientV2Singletons.captureServerInfo(client);
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ClickHouseScope onEnter(
        @Advice.This Client client, @Advice.Argument(0) @Nullable String sqlQuery) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || sqlQuery == null) {
        return null;
      }

      // the constructor advice snapshots the configured target, so a missing snapshot means the
      // client was built before this instrumentation was applied
      ServerInfo serverInfo = ClickHouseClientV2Singletons.serverInfo(client);
      if (serverInfo == null) {
        serverInfo = ServerInfo.empty();
      }

      String database = client.getConfiguration().get("database");
      Context parentContext = currentContext();
      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(
              serverInfo.getAddress(),
              serverInfo.getPort(),
              serverInfo.getAddressGroup(),
              database,
              sqlQuery);

      return ClickHouseScope.start(instrumenter(), parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable ClickHouseScope scope) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
