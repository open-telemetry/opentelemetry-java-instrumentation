/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

/**
 * Wraps a cluster invoker to publish the consumer registry address for the current thread while the
 * delegate chain runs (for example into the Dubbo consumer protocol filter chain).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class RegistryCapturingInvoker<T> implements Invoker<T> {

  private final Invoker<T> delegate;
  private final String registryAddress;

  RegistryCapturingInvoker(Invoker<T> delegate, String registryAddress) {
    this.delegate = delegate;
    this.registryAddress = registryAddress;
  }

  @Override
  public Class<T> getInterface() {
    return delegate.getInterface();
  }

  @Override
  public URL getUrl() {
    return delegate.getUrl();
  }

  @Override
  public boolean isAvailable() {
    return delegate.isAvailable();
  }

  @Override
  public void destroy() {
    delegate.destroy();
  }

  @Override
  public Result invoke(Invocation invocation) {
    DubboRegistryUtil.pushCapturedRegistryAddress(registryAddress);
    try {
      return delegate.invoke(invocation);
    } finally {
      DubboRegistryUtil.clearCapturedRegistryAddress();
    }
  }
}
