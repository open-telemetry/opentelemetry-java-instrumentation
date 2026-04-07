/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.junit.jupiter.api.Test;

class RegistryCapturingClusterWrapperTest {

  private static final URL DUMMY_URL =
      URL.valueOf("dubbo://192.168.1.100:20880/com.example.Service");

  @Test
  @SuppressWarnings("unchecked")
  void joinDoesNotWrapStaticDirectory() {
    Invoker<Object> innerInvoker = new NoopInvoker(DUMMY_URL);
    RegistryCapturingClusterWrapper wrapper =
        new RegistryCapturingClusterWrapper(new FakeCluster(innerInvoker));
    StubInvoker stub = new StubInvoker(DUMMY_URL);
    StaticDirectory<Object> staticDir = new StaticDirectory<>(singletonList(stub));

    Invoker<Object> out = wrapper.join(staticDir);
    assertThat(out).isSameAs(innerInvoker);
  }

  /**
   * Fake {@link Cluster} that provides both the Dubbo 2.7 {@code join(Directory)} and 3.0.4+ {@code
   * join(Directory, boolean)} signatures, following the same pattern as {@link
   * RegistryCapturingClusterWrapper}.
   */
  @SuppressWarnings("unchecked")
  private static class FakeCluster implements Cluster {
    private final Invoker<?> invoker;

    FakeCluster(Invoker<?> invoker) {
      this.invoker = invoker;
    }

    // Dubbo 2.7 signature
    // @Override — present in 2.7, removed in 3.0.4+
    @SuppressWarnings({"MissingOverride", "UnusedMethod", "UnusedVariable", "EffectivelyPrivate"})
    public <T> Invoker<T> join(Directory<T> directory) {
      return (Invoker<T>) invoker;
    }

    // Dubbo 3.0.4+ signature
    // @Override — present in 3.0.4+, absent in 2.7
    @SuppressWarnings({"MissingOverride", "UnusedMethod", "UnusedVariable", "EffectivelyPrivate"})
    public <T> Invoker<T> join(Directory<T> directory, boolean buildFilterChain) {
      return (Invoker<T>) invoker;
    }
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
