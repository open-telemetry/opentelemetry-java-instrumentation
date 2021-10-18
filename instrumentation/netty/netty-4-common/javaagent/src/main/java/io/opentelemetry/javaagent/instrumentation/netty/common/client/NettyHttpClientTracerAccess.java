/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import java.util.concurrent.atomic.AtomicReference;

public class NettyHttpClientTracerAccess {
  private static final AtomicReference<AbstractNettyHttpClientTracer>
      nettyHttpClientTracerReference = new AtomicReference<>();

  public static AbstractNettyHttpClientTracer getTracer() {
    return nettyHttpClientTracerReference.get();
  }

  public static void setTracer(AbstractNettyHttpClientTracer tracer) {
    nettyHttpClientTracerReference.compareAndSet(null, tracer);
  }
}
