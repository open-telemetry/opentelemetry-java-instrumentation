/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.asynchttpclient.Request;
import org.asynchttpclient.netty.request.NettyRequest;

public class RequestContext {
  private final Context parentContext;
  private final Request request;
  @Nullable private Context context;
  @Nullable private NettyRequest nettyRequest;

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

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Nullable
  public NettyRequest getNettyRequest() {
    return nettyRequest;
  }

  public void setNettyRequest(NettyRequest nettyRequest) {
    this.nettyRequest = nettyRequest;
  }
}
