/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.RegistryCapturingClusterWrapper;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;

public final class RegistryCapturingClusterWrapperProxy implements Cluster {

  private final RegistryCapturingClusterWrapper delegate;

  @SuppressWarnings("unused")
  public RegistryCapturingClusterWrapperProxy(Cluster cluster) {
    this.delegate = new RegistryCapturingClusterWrapper(cluster);
  }

  @Override
  public <T> Invoker<T> join(Directory<T> directory) {
    return delegate.join(directory);
  }

  @SuppressWarnings("unused")
  public <T> Invoker<T> join(Directory<T> directory, boolean buildFilterChain) {
    return delegate.join(directory, buildFilterChain);
  }
}
