/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.stream.Stream;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation")
class DubboRegistryUtilTest {

  private static final URL DUMMY_URL =
      URL.valueOf("dubbo://192.168.1.100:20880/com.example.Service");

  @AfterEach
  void tearDown() {
    DubboRegistryUtil.clearCapturedRegistryAddress();
  }

  @ParameterizedTest
  @MethodSource("serviceTargetProvider")
  void buildServiceTarget(String urlString, String expected) {
    assertThat(DubboRegistryUtil.buildServiceTarget(URL.valueOf(urlString))).isEqualTo(expected);
  }

  static Stream<Arguments> serviceTargetProvider() {
    return Stream.of(
        Arguments.of(
            "dubbo://192.168.1.100:20880/com.example.HelloService", "com.example.HelloService"),
        Arguments.of(
            "dubbo://192.168.1.100:20880/com.example.HelloService?version=2.0.0",
            "com.example.HelloService:2.0.0"),
        Arguments.of(
            "dubbo://192.168.1.100:20880/com.example.HelloService?version=2.0.0&group=gray",
            "com.example.HelloService:2.0.0:gray"),
        Arguments.of(
            "dubbo://192.168.1.100:20880/com.example.HelloService?group=gray",
            "com.example.HelloService::gray"),
        Arguments.of(
            "dubbo://192.168.1.100:20880/com.example.HelloService?version=&group=",
            "com.example.HelloService"),
        Arguments.of("dubbo://192.168.1.100:20880/", ""),
        Arguments.of("dubbo://192.168.1.100:20880", ""));
  }

  @Test
  void extractRegistryAddressReturnsNullWhenNoInvoker() {
    RpcInvocation invocation = new RpcInvocation();
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation)).isNull();
  }

  /** Tests the field-based reflection fallback when no {@code getDirectory()} method exists. */
  @Test
  void extractRegistryAddressFieldFallback() throws Exception {
    Directory<?> dir = mockDirectoryWithRegistry(new FakeZkRegistry());
    FieldOnlyClusterInvoker invoker = new FieldOnlyClusterInvoker(dir, DUMMY_URL);
    RpcInvocation invocation = new RpcInvocation();
    invocation.setInvoker(invoker);
    assertThat(DubboRegistryUtil.extractRegistryAddress(invocation))
        .isEqualTo("zookeeper://10.0.0.1:2181");
  }

  @SuppressWarnings("all")
  abstract static class MockableRegistryDirectory implements Directory<Object> {
    Object registry;
  }

  @SuppressWarnings("unchecked")
  private static Directory<?> mockDirectoryWithRegistry(Object registry) throws Exception {
    MockableRegistryDirectory dir = mock(MockableRegistryDirectory.class);
    Field f = MockableRegistryDirectory.class.getDeclaredField("registry");
    f.setAccessible(true);
    f.set(dir, registry);
    return dir;
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

  static class FakeZkRegistry {
    @SuppressWarnings("unused")
    public URL getUrl() {
      return URL.valueOf("zookeeper://10.0.0.1:2181/org.apache.dubbo.registry.RegistryService");
    }
  }
}
