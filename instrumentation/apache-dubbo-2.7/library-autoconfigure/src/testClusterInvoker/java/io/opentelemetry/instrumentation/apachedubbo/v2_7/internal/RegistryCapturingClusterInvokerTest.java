/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.junit.jupiter.api.Test;

class RegistryCapturingClusterInvokerTest {

  private static final String REGISTRY_ADDRESS = "zookeeper://localhost:2181";
  private static final URL REGISTRY_URL = URL.valueOf(REGISTRY_ADDRESS);
  private static final URL SERVICE_URL = URL.valueOf("dubbo://localhost:20880/example.Service");

  @Test
  void preservesClusterInvokerContractAndCapturesRegistryAddress() {
    FakeClusterInvoker delegate = new FakeClusterInvoker();

    Invoker<Object> wrapped = wrapThroughCluster(delegate);

    assertThat(wrapped).isInstanceOf(ClusterInvoker.class);
    ClusterInvoker<Object> clusterInvoker = (ClusterInvoker<Object>) wrapped;
    assertThat(clusterInvoker.getRegistryUrl()).isSameAs(REGISTRY_URL);
    assertThat(clusterInvoker.getDirectory()).isSameAs(delegate.getDirectory());
    assertThat(clusterInvoker.isDestroyed()).isFalse();
    assertThat(clusterInvoker.equals(clusterInvoker)).isTrue();
    assertThat(clusterInvoker.hashCode()).isEqualTo(System.identityHashCode(clusterInvoker));
    assertThat(clusterInvoker.toString())
        .isEqualTo(
            RegistryCapturingInvoker.class.getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(clusterInvoker)));

    Result result = clusterInvoker.invoke(new RpcInvocation());

    assertThat(result).isSameAs(delegate.result);
    assertThat(delegate.capturedRegistryAddress).isEqualTo(REGISTRY_ADDRESS);
    assertThat(DubboRegistryUtil.extractRegistryAddress(new RpcInvocation())).isNull();
  }

  @Test
  void unwrapsDelegateExceptionAndRestoresRegistryAddress() {
    FakeClusterInvoker delegate = new FakeClusterInvoker();
    RpcException failure = new RpcException("expected");
    delegate.failure = failure;
    Invoker<Object> wrapped = wrapThroughCluster(delegate);

    assertThatThrownBy(() -> wrapped.invoke(new RpcInvocation())).isSameAs(failure);
    assertThat(DubboRegistryUtil.extractRegistryAddress(new RpcInvocation())).isNull();
  }

  private static Invoker<Object> wrapThroughCluster(FakeClusterInvoker delegate) {
    RegistryCapturingClusterWrapper wrapper =
        new RegistryCapturingClusterWrapper(new FakeCluster(delegate));
    return wrapper.join(delegate.getDirectory());
  }

  private static class FakeCluster implements Cluster {
    private final Invoker<?> invoker;

    FakeCluster(Invoker<?> invoker) {
      this.invoker = invoker;
    }

    @SuppressWarnings({
      "unchecked",
      "UnusedMethod",
      "UnusedVariable",
      "MissingOverride",
      "EffectivelyPrivate"
    })
    public <T> Invoker<T> join(Directory<T> directory) {
      return (Invoker<T>) invoker;
    }

    @SuppressWarnings({
      "unchecked",
      "UnusedMethod",
      "UnusedVariable",
      "MissingOverride",
      "EffectivelyPrivate"
    })
    public <T> Invoker<T> join(Directory<T> directory, boolean buildFilterChain) {
      return (Invoker<T>) invoker;
    }
  }

  private static class FakeClusterInvoker implements ClusterInvoker<Object> {
    private final Directory<Object> directory = new FakeDirectory();
    private final Result result = new AppResponse("ok");

    private String capturedRegistryAddress;
    private RpcException failure;

    @Override
    public URL getRegistryUrl() {
      return REGISTRY_URL;
    }

    @Override
    public Directory<Object> getDirectory() {
      return directory;
    }

    @Override
    public boolean isDestroyed() {
      return false;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public Result invoke(Invocation invocation) {
      capturedRegistryAddress =
          DubboRegistryUtil.extractRegistryAddress((RpcInvocation) invocation);
      if (failure != null) {
        throw failure;
      }
      return result;
    }

    @Override
    public URL getUrl() {
      return SERVICE_URL;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  static class FakeDirectory implements Directory<Object> {

    private final FakeRegistry registry = new FakeRegistry();

    public FakeRegistry getRegistry() {
      return registry;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public List<Invoker<Object>> list(Invocation invocation) {
      return emptyList();
    }

    @Override
    public List<Invoker<Object>> getAllInvokers() {
      return emptyList();
    }

    @Override
    public URL getConsumerUrl() {
      return SERVICE_URL;
    }

    @Override
    public boolean isDestroyed() {
      return false;
    }

    @Override
    public void discordAddresses() {}

    @SuppressWarnings({"UnusedMethod", "MissingOverride"})
    public RouterChain<Object> getRouterChain() {
      return null;
    }

    @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MissingOverride"})
    public void addInvalidateInvoker(Invoker<Object> invoker) {}

    @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MissingOverride"})
    public void addDisabledInvoker(Invoker<Object> invoker) {}

    @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MissingOverride"})
    public void recoverDisabledInvoker(Invoker<Object> invoker) {}

    @Override
    public URL getUrl() {
      return SERVICE_URL;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  static class FakeRegistry {

    public URL getUrl() {
      return REGISTRY_URL;
    }
  }
}
