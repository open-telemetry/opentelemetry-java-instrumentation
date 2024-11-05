/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import io.netty.channel.Channel;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.ContextHolder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import ratpack.exec.Execution;

public final class RatpackSingletons {

  static {
    TELEMETRY =
        RatpackTelemetry.builder(GlobalOpenTelemetry.get())
            .configure(AgentCommonConfig.get())
            .build();
  }

  private static final Instrumenter<String, Void> INSTRUMENTER =
      Instrumenter.<String, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.ratpack-1.7", s -> s)
          .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
          .buildInstrumenter();

  public static Instrumenter<String, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private static final RatpackTelemetry TELEMETRY;

  public static RatpackTelemetry telemetry() {
    return TELEMETRY;
  }

  public static void propagateContextToChannel(Execution execution, Channel channel) {
    Context parentContext =
        execution
            .maybeGet(ContextHolder.class)
            .map(ContextHolder::context)
            .orElse(Context.current());
    channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(parentContext);
  }

  private RatpackSingletons() {}
}
