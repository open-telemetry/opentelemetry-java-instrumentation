/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil.NOOP_OPERATION_LISTENER;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class TracingHolder {

  public static final Tracing TRACING =
      LettuceTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(AgentCommonConfig.get().isStatementSanitizationEnabled())
          .setMetrics(
              meter ->
                  NOOP_OPERATION_LISTENER) // javaagent uses bytecode instrumentation for metrics
          .build()
          .newTracing();

  private TracingHolder() {}
}
