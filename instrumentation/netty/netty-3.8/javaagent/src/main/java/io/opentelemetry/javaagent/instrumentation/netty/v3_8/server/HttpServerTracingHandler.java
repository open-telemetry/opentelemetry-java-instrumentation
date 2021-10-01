/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.CombinedSimpleChannelHandler;
import org.jboss.netty.channel.Channel;

public class HttpServerTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler(VirtualField<Channel, ChannelTraceContext> virtualField) {
    super(
        new HttpServerRequestTracingHandler(virtualField),
        new HttpServerResponseTracingHandler(virtualField));
  }
}
