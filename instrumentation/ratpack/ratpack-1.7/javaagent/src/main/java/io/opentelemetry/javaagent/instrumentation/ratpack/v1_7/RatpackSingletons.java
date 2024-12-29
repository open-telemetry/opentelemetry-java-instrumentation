/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import io.netty.channel.Channel;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.ContextHolder;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryHttpClient;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.RatpackClientInstrumenterBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import ratpack.exec.Execution;

public final class RatpackSingletons {

  static {
    HTTP_CLIENT =
        new OpenTelemetryHttpClient(
            RatpackClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.ratpack-1.7", GlobalOpenTelemetry.get())
                .configure(AgentCommonConfig.get())
                .build());
  }

  private static final OpenTelemetryHttpClient HTTP_CLIENT;

  public static OpenTelemetryHttpClient httpClient() {
    return HTTP_CLIENT;
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
