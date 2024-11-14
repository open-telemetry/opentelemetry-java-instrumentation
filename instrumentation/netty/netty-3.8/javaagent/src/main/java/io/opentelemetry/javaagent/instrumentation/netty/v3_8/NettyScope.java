/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.common.internal.Timer;

/** Container used to carry state between enter and exit advices */
public class NettyScope {

  private final Context parentContext;
  private final NettyConnectionRequest request;
  private final Timer timer;

  public NettyScope(Context parentContext, NettyConnectionRequest request, Timer timer) {
    this.parentContext = parentContext;
    this.request = request;
    this.timer = timer;
  }

  public Context getParentContext() {
    return parentContext;
  }

  public NettyConnectionRequest getRequest() {
    return request;
  }

  public Timer getTimer() {
    return timer;
  }
}
