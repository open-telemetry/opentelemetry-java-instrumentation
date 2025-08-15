/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

@AutoValue
public abstract class DubboRequest {

  static DubboRequest create(RpcInvocation invocation, RpcContext context) {
    // In dubbo 3 RpcContext delegates to a ThreadLocal context. We copy the url and remote address
    // here to ensure we can access them from the thread that ends the span.

    // There is a compatibility issue with context.getRemoteAddress() in 3.x.
    // In some versions, context.getRemoteAddress() may return null.
    // Use context.getUrl().toInetSocketAddress() here directly, related bugfix PR:
    // https://github.com/apache/dubbo/issues/11790
    return new AutoValue_DubboRequest(
        invocation,
        context,
        context.getUrl(),
        context.getUrl().toInetSocketAddress(),
        context.getLocalAddress());
  }

  abstract RpcInvocation invocation();

  public abstract RpcContext context();

  public abstract URL url();

  @Nullable
  public abstract InetSocketAddress remoteAddress();

  @Nullable
  public abstract InetSocketAddress localAddress();
}
