/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class TracingHolder {

  public static final Tracing TRACING =
      LettuceTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(CommonConfig.get().isStatementSanitizationEnabled())
          .build()
          .newTracing();

  private TracingHolder() {}
}
