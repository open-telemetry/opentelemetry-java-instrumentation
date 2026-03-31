/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboRegistryUtil;
import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class DubboRegistryUtilTest {

  private static final URL DUMMY_URL =
      URL.valueOf("dubbo://192.168.1.100:20880/com.example.Service");

  @Test
  void extractRegistryAddress_returnsAddressForRegistryDirectory() {
    FakeClusterInvoker invoker = new FakeClusterInvoker(new FakeRegistryDirectory(), DUMMY_URL);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setInvoker(invoker);
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation))
        .isEqualTo("nacos://127.0.0.1:8848");
  }

  @Test
  void extractRegistryAddress_returnsNullForStaticDirectory() {
    StubInvoker stub = new StubInvoker(DUMMY_URL);
    StaticDirectory<?> staticDir = new StaticDirectory<>(DUMMY_URL, singletonList(stub));
    FakeClusterInvoker invoker = new FakeClusterInvoker(staticDir, DUMMY_URL);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setInvoker(invoker);
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation)).isNull();
  }

  @Test
  void extractRegistryAddress_returnsNullWhenNoInvoker() {
    RpcInvocation invocation = new RpcInvocation();
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation)).isNull();
  }

  @Test
  void extractRegistryAddress_returnsNullWhenInvokerHasNoDirectory() {
    RpcInvocation invocation = new RpcInvocation();
    invocation.setInvoker(new StubInvoker(DUMMY_URL));
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation)).isNull();
  }

  @Test
  void extractRegistryAddress_fieldFallback_returnsAddressForClusterInvokerWithFieldOnly() {
    FieldOnlyClusterInvoker invoker =
        new FieldOnlyClusterInvoker(new FieldOnlyRegistryDirectory(), DUMMY_URL);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setInvoker(invoker);
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation))
        .isEqualTo("zookeeper://10.0.0.1:2181");
  }

  @Test
  void buildServiceTarget_interfaceOnly() {
    URL url = URL.valueOf("dubbo://192.168.1.100:20880/com.example.HelloService");
    assertThat(DubboRegistryUtil.buildServiceTarget(url)).isEqualTo("com.example.HelloService");
  }

  @Test
  void buildServiceTarget_withVersion() {
    URL url = URL.valueOf("dubbo://192.168.1.100:20880/com.example.HelloService?version=2.0.0");
    assertThat(DubboRegistryUtil.buildServiceTarget(url))
        .isEqualTo("com.example.HelloService:2.0.0");
  }

  @Test
  void buildServiceTarget_withVersionAndGroup() {
    URL url =
        URL.valueOf(
            "dubbo://192.168.1.100:20880/com.example.HelloService?version=2.0.0&group=gray");
    assertThat(DubboRegistryUtil.buildServiceTarget(url))
        .isEqualTo("com.example.HelloService:2.0.0:gray");
  }

  @Test
  void buildServiceTarget_withGroupOnly() {
    URL url = URL.valueOf("dubbo://192.168.1.100:20880/com.example.HelloService?group=gray");
    assertThat(DubboRegistryUtil.buildServiceTarget(url))
        .isEqualTo("com.example.HelloService::gray");
  }

  @Test
  void buildServiceTarget_withEmptyVersionAndGroup() {
    URL url = URL.valueOf("dubbo://192.168.1.100:20880/com.example.HelloService?version=&group=");
    assertThat(DubboRegistryUtil.buildServiceTarget(url)).isEqualTo("com.example.HelloService");
  }

  @SuppressWarnings({"unused", "UnusedMethod", "MethodCanBeStatic", "EffectivelyPrivate"})
  private static class FakeClusterInvoker implements Invoker<Object> {

    private final Directory<?> directory;
    private final URL url;

    FakeClusterInvoker(Directory<?> directory, URL url) {
      this.directory = directory;
      this.url = url;
    }

    public Directory<?> getDirectory() {
      return directory;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public Result invoke(Invocation invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  private static class StubInvoker implements Invoker<Object> {

    private final URL url;

    StubInvoker(URL url) {
      this.url = url;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public Result invoke(Invocation invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  @SuppressWarnings({
    "rawtypes",
    "unchecked",
    "EffectivelyPrivate",
    "UnusedMethod",
    "MethodCanBeStatic"
  })
  private static class FakeRegistryDirectory implements Directory<Object> {

    public FakeRegistry getRegistry() {
      return new FakeRegistry();
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
    public URL getUrl() {
      return URL.valueOf("dubbo://localhost:20880/com.example.Service");
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  static class FakeRegistry {
    @SuppressWarnings("unused")
    public URL getUrl() {
      return URL.valueOf("nacos://127.0.0.1:8848/org.apache.dubbo.registry.RegistryService");
    }
  }

  @SuppressWarnings({"unused", "EffectivelyPrivate"})
  private static class FieldOnlyClusterInvoker implements Invoker<Object> {

    private final Directory<?> directory;
    private final URL url;

    FieldOnlyClusterInvoker(Directory<?> directory, URL url) {
      this.directory = directory;
      this.url = url;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public Result invoke(Invocation invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  @SuppressWarnings({"rawtypes", "unchecked", "unused", "EffectivelyPrivate"})
  private static class FieldOnlyRegistryDirectory implements Directory<Object> {

    private final FakeZkRegistry registry = new FakeZkRegistry();

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
    public URL getUrl() {
      return URL.valueOf("dubbo://localhost:20880/com.example.Service");
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public void destroy() {}
  }

  static class FakeZkRegistry {
    @SuppressWarnings("unused")
    public URL getUrl() {
      return URL.valueOf("zookeeper://10.0.0.1:2181/org.apache.dubbo.registry.RegistryService");
    }
  }
}
