/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.AbstractNettyHttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyHttpClientTracerAccess;

public class NettyHttpClientTracer extends AbstractNettyHttpClientTracer<NettyRequestWrapper> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  static {
    NettyHttpClientTracerAccess.setTracer(TRACER);
  }

  private NettyHttpClientTracer() {}

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.netty-4.0";
  }
}
