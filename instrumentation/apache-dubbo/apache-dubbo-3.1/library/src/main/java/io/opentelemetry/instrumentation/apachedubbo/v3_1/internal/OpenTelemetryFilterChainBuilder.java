/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1.internal;

import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.model.ServiceModel;

/**
 * Dubbo SPI wrapper for supplied-SDK provider tracing.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class OpenTelemetryFilterChainBuilder implements FilterChainBuilder {

  public static final String KEY = OpenTelemetryFilterChainBuilder.class.getName();

  private final FilterChainBuilder delegate;

  public OpenTelemetryFilterChainBuilder(FilterChainBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public <T> Invoker<T> buildInvokerChain(Invoker<T> invoker, String key, String group) {
    Invoker<T> chain = delegate.buildInvokerChain(invoker, key, group);
    ServiceModel model = invoker.getUrl().getServiceModel();
    Object filter =
        PROVIDER.equals(group) && model instanceof ProviderModel
            ? model.getServiceMetadata().getAttribute(KEY)
            : null;
    if (!(filter instanceof Filter)) {
      return chain;
    }
    return new Invoker<T>() {
      @Override
      public Class<T> getInterface() {
        return chain.getInterface();
      }

      @Override
      public URL getUrl() {
        return chain.getUrl();
      }

      @Override
      public boolean isAvailable() {
        return chain.isAvailable();
      }

      @Override
      public Result invoke(Invocation invocation) {
        return ((Filter) filter).invoke(chain, invocation);
      }

      @Override
      public void destroy() {
        chain.destroy();
      }
    };
  }

  @Override
  public <T> ClusterInvoker<T> buildClusterInvokerChain(
      ClusterInvoker<T> invoker, String key, String group) {
    return delegate.buildClusterInvokerChain(invoker, key, group);
  }
}
