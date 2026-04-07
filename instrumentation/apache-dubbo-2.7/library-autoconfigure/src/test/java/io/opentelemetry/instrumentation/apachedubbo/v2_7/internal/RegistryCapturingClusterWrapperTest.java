/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.junit.jupiter.api.Test;

class RegistryCapturingClusterWrapperTest {

  private static final URL DUMMY_URL =
      URL.valueOf("dubbo://192.168.1.100:20880/com.example.Service");

  @Test
  @SuppressWarnings("unchecked")
  void joinDoesNotWrapStaticDirectory() {
    Cluster inner = mock(Cluster.class);
    RegistryCapturingClusterWrapper wrapper = new RegistryCapturingClusterWrapper(inner);
    Invoker<Object> innerInvoker = new NoopInvoker(DUMMY_URL);
    StubInvoker stub = new StubInvoker(DUMMY_URL);
    StaticDirectory<Object> staticDir = new StaticDirectory<>(singletonList(stub));
    when(inner.join(same(staticDir))).thenReturn(innerInvoker);

    Invoker<Object> out = wrapper.join(staticDir);
    assertThat(out).isSameAs(innerInvoker);
  }

  private static class NoopInvoker implements Invoker<Object> {
    private final URL url;

    NoopInvoker(URL url) {
      this.url = url;
    }

    @Override
    public Class<Object> getInterface() {
      return Object.class;
    }

    @Override
    public Result invoke(Invocation invocation) {
      return null;
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
}
