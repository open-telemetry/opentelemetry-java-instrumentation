/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.opentelemetry.javaagent.instrumentation.netty.common.client.AbstractNettyHttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyHttpClientTracerAccess;

public class NettyHttpClientTracer extends AbstractNettyHttpClientTracer {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  static {
    NettyHttpClientTracerAccess.setTracer(TRACER);
  }

  private NettyHttpClientTracer() {}

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.netty-4.1";
  }
}
