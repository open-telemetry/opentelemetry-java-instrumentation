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
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.RatpackHttpProtocolVersion;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import ratpack.exec.Execution;
import ratpack.http.client.RequestSpec;

public class RatpackSingletons {

  private static final OpenTelemetryHttpClient httpClient;

  static {
    httpClient =
        new OpenTelemetryHttpClient(
            RatpackClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.ratpack-1.7", GlobalOpenTelemetry.get())
                .addAttributesExtractor(new RatpackProtocolVersionAttributesExtractor())
                .configure(AgentCommonConfig.get())
                .build());
  }

  public static OpenTelemetryHttpClient httpClient() {
    return httpClient;
  }

  public static void propagateContextToChannel(Execution execution, Channel channel) {
    Context parentContext =
        execution
            .maybeGet(ContextHolder.class)
            .map(ContextHolder::context)
            .orElse(Context.current());
    channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(parentContext);
  }

  public static void captureProtocolVersion(Execution execution, Channel channel) {
    RequestSpec request =
        execution.maybeGet(ContextHolder.class).map(ContextHolder::requestSpec).orElse(null);
    if (request != null) {
      RatpackHttpProtocolVersion.attach(request, channel);
    }
  }

  private RatpackSingletons() {}
}
