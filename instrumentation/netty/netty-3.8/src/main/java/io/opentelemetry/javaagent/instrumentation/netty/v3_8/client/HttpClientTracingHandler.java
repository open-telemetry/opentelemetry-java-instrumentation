/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v3_8.client;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.instrumentation.auto.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.instrumentation.auto.netty.v3_8.util.CombinedSimpleChannelHandler;
import org.jboss.netty.channel.Channel;

public class HttpClientTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpClientResponseTracingHandler, HttpClientRequestTracingHandler> {

  public HttpClientTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    super(
        new HttpClientResponseTracingHandler(contextStore),
        new HttpClientRequestTracingHandler(contextStore));
  }
}
