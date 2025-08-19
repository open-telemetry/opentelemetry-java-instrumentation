/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.v0_6;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QuerySettings;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseScope;
import java.net.URI;
import java.util.Map;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ClickHouseClientV2QueryAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ClickHouseScope onEnter(
      @Advice.This Client client,
      @Advice.Argument(0) String sqlQuery,
      @Advice.Argument(1) Map<String, Object> queryParams,
      @Advice.Argument(2) QuerySettings querySettings) {
    Instrumenter<ClickHouseDbRequest, Void> instrumenter =
        ClickHouseClientV2Singletons.instrumenter();

    CallDepth callDepth = CallDepth.forClass(Client.class);
    if (callDepth.getAndIncrement() > 0 || sqlQuery == null) {
      return null;
    }

    String endPoint = client.getEndpoints().stream().findFirst().orElse(null);
    String host = null;
    int port = 0;

    if (endPoint != null) {
      URI uri = URI.create(endPoint);
      host = uri.getHost();
      port = uri.getPort();
    }

    String database = client.getConfiguration().get("database");

    Context parentContext = currentContext();

    ClickHouseDbRequest request = ClickHouseDbRequest.create(host, port, database, sqlQuery);

    return ClickHouseScope.start(instrumenter, parentContext, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {
    Instrumenter<ClickHouseDbRequest, Void> instrumenter =
        ClickHouseClientV2Singletons.instrumenter();

    CallDepth callDepth = CallDepth.forClass(Client.class);
    if (callDepth.decrementAndGet() > 0 || scope == null) {
      return;
    }

    scope.end(instrumenter, throwable);
  }
}
