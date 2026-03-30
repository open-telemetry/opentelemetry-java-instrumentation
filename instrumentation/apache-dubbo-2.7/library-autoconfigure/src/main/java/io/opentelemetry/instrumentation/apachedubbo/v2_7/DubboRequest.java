/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

public final class DubboRequest {

  private final RpcInvocation invocation;
  @Nullable private final RpcContext context;
  @Nullable private final URL url;
  @Nullable private final InetSocketAddress remoteAddress;
  @Nullable private final InetSocketAddress localAddress;
  @Nullable private final String originalFullMethodName;

  @SuppressWarnings("deprecation") // RpcContext.getContext()
  static DubboRequest create(RpcInvocation invocation, RpcContext context) {
    return new DubboRequest(
        invocation,
        context,
        context.getUrl(),
        context.getRemoteAddress(),
        context.getLocalAddress(),
        null);
  }

  public static DubboRequest createForUnknownService(
      RpcInvocation invocation,
      String originalFullMethodName,
      @Nullable InetSocketAddress remoteAddress,
      @Nullable InetSocketAddress localAddress) {
    return new DubboRequest(
        invocation, null, null, remoteAddress, localAddress, originalFullMethodName);
  }

  private DubboRequest(
      RpcInvocation invocation,
      @Nullable RpcContext context,
      @Nullable URL url,
      @Nullable InetSocketAddress remoteAddress,
      @Nullable InetSocketAddress localAddress,
      @Nullable String originalFullMethodName) {
    this.invocation = invocation;
    this.context = context;
    this.url = url;
    this.remoteAddress = remoteAddress;
    this.localAddress = localAddress;
    this.originalFullMethodName = originalFullMethodName;
  }

  RpcInvocation invocation() {
    return invocation;
  }

  @Nullable
  public RpcContext context() {
    return context;
  }

  @Nullable
  public URL url() {
    return url;
  }

  @Nullable
  public InetSocketAddress remoteAddress() {
    return remoteAddress;
  }

  @Nullable
  public InetSocketAddress localAddress() {
    return localAddress;
  }

  @Nullable
  String originalFullMethodName() {
    return originalFullMethodName;
  }

  boolean isUnknownService() {
    return originalFullMethodName != null;
  }
}
