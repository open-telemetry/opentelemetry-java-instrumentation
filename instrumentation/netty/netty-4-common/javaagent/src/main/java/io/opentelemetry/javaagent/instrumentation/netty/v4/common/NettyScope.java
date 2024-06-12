/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;

/** Container used to carry state between enter and exit advices */
public class NettyScope {

  Context context;
  NettyConnectionRequest request;
  Scope scope;

  private NettyScope(Context context, NettyConnectionRequest request, Scope scope) {
    this.context = context;
    this.request = request;
    this.scope = scope;
  }

  public static NettyScope create(Context context, NettyConnectionRequest request, Scope scope) {
    return new NettyScope(context, request, scope);
  }

  public Context getContext() {
    return context;
  }

  public NettyConnectionRequest getRequest() {
    return request;
  }

  public Scope getScope() {
    return scope;
  }
}
