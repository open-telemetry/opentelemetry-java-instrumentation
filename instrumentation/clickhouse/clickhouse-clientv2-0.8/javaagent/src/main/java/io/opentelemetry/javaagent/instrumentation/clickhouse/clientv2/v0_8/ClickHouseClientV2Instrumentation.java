/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QuerySettings;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseScope;
import java.util.Map;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClickHouseClientV2Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("query"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, isSubTypeOf(Map.class)))
            .and(takesArgument(2, named("com.clickhouse.client.api.query.QuerySettings"))),
        this.getClass().getName() + "$ClickHouseClientV2QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClickHouseClientV2QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ClickHouseScope onEnter(
        @Advice.This Client client,
        @Advice.Argument(0) String sqlQuery,
        @Advice.Argument(1) Map<String, Object> queryParams,
        @Advice.Argument(2) QuerySettings querySettings) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || sqlQuery == null) {
        return null;
      }

      String endPoint = client.getEndpoints().stream().findFirst().orElse(null);
      String host = null;
      Integer port = 0;

      if (endPoint != null) {
        host = UrlParser.getHost(endPoint);
        port = Optional.ofNullable(UrlParser.getPort(endPoint)).orElse(0);
      }

      String database = client.getConfiguration().get("database");
      Context parentContext = currentContext();
      ClickHouseDbRequest request = ClickHouseDbRequest.create(host, port, database, sqlQuery);

      return ClickHouseScope.start(
          ClickHouseClientV2Singletons.instrumenter(), parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(ClickHouseClientV2Singletons.instrumenter(), throwable);
    }
  }
}
