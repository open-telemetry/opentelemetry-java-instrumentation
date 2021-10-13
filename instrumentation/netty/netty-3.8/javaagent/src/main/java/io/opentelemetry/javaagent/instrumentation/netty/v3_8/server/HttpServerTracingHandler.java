/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.CombinedSimpleChannelHandler;

public class HttpServerTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler() {
    super(new HttpServerRequestTracingHandler(), new HttpServerResponseTracingHandler());
  }
}
