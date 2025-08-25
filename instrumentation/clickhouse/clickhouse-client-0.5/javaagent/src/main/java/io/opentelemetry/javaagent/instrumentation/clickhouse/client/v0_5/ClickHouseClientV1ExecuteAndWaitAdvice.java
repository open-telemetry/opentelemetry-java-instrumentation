/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.v0_5;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseRequestAccess;
import com.clickhouse.client.config.ClickHouseDefaults;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseScope;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ClickHouseClientV1ExecuteAndWaitAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ClickHouseScope onEnter(
      @Advice.Argument(0) ClickHouseRequest<?> clickHouseRequest) {
    Instrumenter<ClickHouseDbRequest, Void> instrumenter =
        ClickHouseClientV1Singletons.instrumenter();

    CallDepth callDepth = CallDepth.forClass(ClickHouseClient.class);
    if (callDepth.getAndIncrement() > 0 || clickHouseRequest == null) {
      return null;
    }

    Context parentContext = currentContext();

    ClickHouseDbRequest request =
        ClickHouseDbRequest.create(
            clickHouseRequest.getServer().getHost(),
            clickHouseRequest.getServer().getPort(),
            clickHouseRequest
                .getServer()
                .getDatabase()
                .orElse(ClickHouseDefaults.DATABASE.getDefaultValue().toString()),
            ClickHouseRequestAccess.getQuery(clickHouseRequest));

    return ClickHouseScope.start(instrumenter, parentContext, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {

    Instrumenter<ClickHouseDbRequest, Void> instrumenter =
        ClickHouseClientV1Singletons.instrumenter();

    CallDepth callDepth = CallDepth.forClass(ClickHouseClient.class);
    if (callDepth.decrementAndGet() > 0 || scope == null) {
      return;
    }

    scope.end(instrumenter, throwable);
  }
}
