/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.CombinedSimpleChannelHandler;
import org.jboss.netty.channel.Channel;

public class HttpServerTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    super(
        new HttpServerRequestTracingHandler(contextStore),
        new HttpServerResponseTracingHandler(contextStore));
  }
}
