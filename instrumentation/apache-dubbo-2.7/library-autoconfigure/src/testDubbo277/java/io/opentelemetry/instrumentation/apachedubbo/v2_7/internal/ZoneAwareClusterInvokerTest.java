/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareClusterInvoker;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterInvoker;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper;
import org.junit.jupiter.api.Test;

class ZoneAwareClusterInvokerTest {

  @Test
  void routesToPreferredRegistryAndCapturesItsAddress() {
    Invoker<Object> first = registryInvoker("zookeeper://localhost:2181");
    Invoker<Object> preferred =
        registryInvoker("zookeeper://localhost:2182?registry.preferred=true");
    ZoneAwareClusterInvoker<Object> router =
        new ZoneAwareClusterInvoker<>(new StaticDirectory<>(asList(first, preferred)));
    RpcInvocation invocation = new RpcInvocation();
    invocation.setMethodName("echo");

    Result result = router.invoke(invocation);

    assertThat(result.getValue()).isEqualTo("zookeeper://localhost:2182");
    assertThat(DubboRegistryUtil.extractRegistryAddress(new RpcInvocation())).isNull();
  }

  @Test
  void preservesCustomMockClusterInvoker() {
    RegistryBackedDirectory directory = registryDirectory("zookeeper://localhost:2181");
    @SuppressWarnings("unchecked")
    Invoker<Object> provider = mock(Invoker.class);
    MockClusterInvoker<Object> custom =
        new MockClusterInvoker<Object>(directory, provider) {
          @Override
          public boolean isAvailable() {
            return false;
          }
        };
    Cluster cluster = mock();
    when(cluster.join(directory)).thenReturn(custom);

    assertThat(new RegistryCapturingClusterWrapper(cluster).join(directory)).isSameAs(custom);
  }

  private static Invoker<Object> registryInvoker(String address) {
    RegistryBackedDirectory directory = registryDirectory(address);
    @SuppressWarnings("unchecked")
    Invoker<Object> provider = mock(Invoker.class);
    when(provider.invoke(any()))
        .thenAnswer(
            call -> new AppResponse(DubboRegistryUtil.extractRegistryAddress(call.getArgument(0))));
    Cluster cluster = mock();
    when(cluster.join(directory)).thenReturn(provider);
    return new RegistryCapturingClusterWrapper(new MockClusterWrapper(cluster)).join(directory);
  }

  private static RegistryBackedDirectory registryDirectory(String address) {
    URL url = URL.valueOf(address);
    Registry registry = mock();
    when(registry.getUrl()).thenReturn(url);
    RegistryBackedDirectory directory = mock();
    when(directory.getRegistry()).thenReturn(registry);
    when(directory.getUrl()).thenReturn(url);
    when(directory.getConsumerUrl())
        .thenReturn(URL.valueOf("dubbo://localhost:20880/example.Service"));
    when(directory.isAvailable()).thenReturn(true);
    return directory;
  }

  interface RegistryBackedDirectory extends Directory<Object> {
    Registry getRegistry();
  }
}
