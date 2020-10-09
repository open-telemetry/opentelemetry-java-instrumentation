/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v4_1.server;

import io.netty.channel.CombinedChannelDuplexHandler;

public class HttpServerTracingHandler
    extends CombinedChannelDuplexHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler() {
    super(new HttpServerRequestTracingHandler(), new HttpServerResponseTracingHandler());
  }
}
