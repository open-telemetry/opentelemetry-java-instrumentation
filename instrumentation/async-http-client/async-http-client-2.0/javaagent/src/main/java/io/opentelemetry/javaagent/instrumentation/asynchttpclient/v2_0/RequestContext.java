/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.context.Context;
import org.asynchttpclient.Request;
import org.asynchttpclient.netty.request.NettyRequest;

public final class RequestContext {
  private final Context parentContext;
  private final Request request;
  private Context context;
  private NettyRequest nettyRequest;

  public RequestContext(Context parentContext, Request request) {
    this.parentContext = parentContext;
    this.request = request;
  }

  public Context getParentContext() {
    return parentContext;
  }

  public Request getRequest() {
    return request;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  public NettyRequest getNettyRequest() {
    return nettyRequest;
  }

  public void setNettyRequest(NettyRequest nettyRequest) {
    this.nettyRequest = nettyRequest;
  }
}
