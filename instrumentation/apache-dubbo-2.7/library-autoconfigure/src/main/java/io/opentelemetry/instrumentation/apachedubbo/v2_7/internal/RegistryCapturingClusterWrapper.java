/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;

/**
 * Dubbo {@link Cluster} SPI wrapper that records the registry URL for each registry-backed {@link
 * Directory} when the cluster invoker is entered, so consumer {@link org.apache.dubbo.rpc.Filter}
 * implementations can read it from {@link DubboRegistryUtil}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RegistryCapturingClusterWrapper implements Cluster {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  /** Dubbo 3.0.4+: join(Directory, boolean). Absent on 2.7 and 3.0.0-3.0.3. */
  @Nullable private static final MethodHandle JOIN_TWO_ARG;

  /** Dubbo 2.7 and 3.0.0-3.0.3: join(Directory). Absent on 3.0.4+. */
  @Nullable private static final MethodHandle JOIN_ONE_ARG;

  static {
    MethodHandle two = null;
    MethodHandle one = null;
    try {
      Method m = Cluster.class.getMethod("join", Directory.class, boolean.class);
      two = LOOKUP.unreflect(m);
    } catch (ReflectiveOperationException ignored) {
      // Dubbo 2.7 / 3.0.0-3.0.3
    }
    try {
      Method m = Cluster.class.getMethod("join", Directory.class);
      one = LOOKUP.unreflect(m);
    } catch (ReflectiveOperationException ignored) {
      // Dubbo 3.0.4+
    }
    JOIN_TWO_ARG = two;
    JOIN_ONE_ARG = one;
  }

  private final Cluster cluster;

  /** SPI wrapper constructor — must accept the delegate {@link Cluster}. */
  @SuppressWarnings("unused") // Used by Dubbo ExtensionLoader via reflection
  public RegistryCapturingClusterWrapper(Cluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public <T> Invoker<T> join(Directory<T> directory) {
    return wrapIfNeeded(directory, delegateJoin(cluster, directory, true));
  }

  /**
   * Dubbo 3.0.4+ entry point. Not an {@code @Override} when compiling against Dubbo 2.7, but
   * required for {@link Cluster} at runtime on 3.0.4+.
   */
  @SuppressWarnings("unused")
  public <T> Invoker<T> join(Directory<T> directory, boolean buildFilterChain) {
    return wrapIfNeeded(directory, delegateJoin(cluster, directory, buildFilterChain));
  }

  /**
   * {@link MethodHandle#invoke} is untyped; the returned invoker matches the generic {@code T} from
   * the {@code directory} argument and delegate {@link Cluster#join}.
   */
  @SuppressWarnings("unchecked")
  private static <T> Invoker<T> delegateJoin(
      Cluster cluster, Directory<T> directory, boolean buildFilterChain) {
    try {
      if (JOIN_TWO_ARG != null) {
        return (Invoker<T>) JOIN_TWO_ARG.invoke(cluster, directory, buildFilterChain);
      }
      if (JOIN_ONE_ARG != null) {
        return (Invoker<T>) JOIN_ONE_ARG.invoke(cluster, directory);
      }
    } catch (RpcException e) {
      throw e;
    } catch (Throwable t) {
      throw new RpcException(t.getMessage(), t);
    }
    throw new RpcException("No join(Directory) or join(Directory,boolean) on Cluster");
  }

  private static <T> Invoker<T> wrapIfNeeded(Directory<T> directory, Invoker<T> invoker) {
    if (isStaticDirectory(directory)) {
      return invoker;
    }
    String registryAddress = DubboRegistryUtil.tryExtractRegistryAddressFromDirectory(directory);
    if (registryAddress == null) {
      return invoker;
    }
    return new RegistryCapturingInvoker<>(invoker, registryAddress);
  }

  private static boolean isStaticDirectory(Directory<?> directory) {
    return directory != null
        && "org.apache.dubbo.rpc.cluster.directory.StaticDirectory"
            .equals(directory.getClass().getName());
  }
}
