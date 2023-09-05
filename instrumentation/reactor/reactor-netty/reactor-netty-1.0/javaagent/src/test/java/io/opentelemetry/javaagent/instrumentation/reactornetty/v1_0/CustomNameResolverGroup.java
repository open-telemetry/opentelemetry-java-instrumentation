/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static java.util.Collections.singletonList;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public class CustomNameResolverGroup extends AddressResolverGroup<InetSocketAddress> {

  public static final CustomNameResolverGroup INSTANCE = new CustomNameResolverGroup();

  private CustomNameResolverGroup() {}

  @Override
  protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
    return new CustomNameResolver(executor).asAddressResolver();
  }

  private static class CustomNameResolver extends InetNameResolver {

    private CustomNameResolver(EventExecutor executor) {
      super(executor);
    }

    @Override
    @SuppressWarnings("AddressSelection")
    protected void doResolve(String inetHost, Promise<InetAddress> promise) {
      try {
        promise.setSuccess(InetAddress.getByName(inetHost));
      } catch (UnknownHostException exception) {
        promise.setFailure(exception);
      }
    }

    @Override
    @SuppressWarnings("AddressSelection")
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) {
      try {
        // default implementation calls InetAddress.getAllByName
        promise.setSuccess(singletonList(InetAddress.getByName(inetHost)));
      } catch (UnknownHostException exception) {
        promise.setFailure(exception);
      }
    }
  }
}
