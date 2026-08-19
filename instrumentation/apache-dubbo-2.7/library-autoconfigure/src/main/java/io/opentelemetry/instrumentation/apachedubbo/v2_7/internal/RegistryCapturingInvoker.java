/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static java.util.logging.Level.FINE;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

/**
 * Wraps a Dubbo invoker to publish the consumer registry address for the current thread while the
 * delegate chain runs (for example into the Dubbo consumer protocol filter chain).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class RegistryCapturingInvoker implements InvocationHandler {

  // ClusterInvoker was introduced in Dubbo 2.7.8. It does not exist in Dubbo 2.7.0 through
  // 2.7.7, which this module supports using a 2.7.0 compile-time dependency. Load the interface
  // reflectively to avoid linking against it when instrumenting Dubbo 2.7.0 through 2.7.7.
  private static final String CLUSTER_INVOKER_CLASS_NAME =
      "org.apache.dubbo.rpc.cluster.ClusterInvoker";

  private static final Logger logger = Logger.getLogger(RegistryCapturingInvoker.class.getName());

  private final Invoker<?> delegate;
  private final String registryAddress;

  static <T> Invoker<T> wrap(Invoker<T> delegate, String registryAddress) {
    Class<?> clusterInvokerClass = getClusterInvokerClass(delegate);
    Class<?> proxyInterface =
        clusterInvokerClass != null && clusterInvokerClass.isInstance(delegate)
            ? clusterInvokerClass
            : Invoker.class;

    try {
      return createProxy(delegate, registryAddress, proxyInterface);
    } catch (RuntimeException | LinkageError e) {
      logger.log(FINE, "Unable to wrap Dubbo Invoker", e);
      return delegate;
    }
  }

  private RegistryCapturingInvoker(Invoker<?> delegate, String registryAddress) {
    this.delegate = delegate;
    this.registryAddress = registryAddress;
  }

  @Nullable
  private static Class<?> getClusterInvokerClass(Invoker<?> delegate) {
    try {
      return Class.forName(CLUSTER_INVOKER_CLASS_NAME, false, delegate.getClass().getClassLoader());
    } catch (ClassNotFoundException | LinkageError ignored) {
      return null;
    }
  }

  // proxyInterface is either Invoker or ClusterInvoker, which extends Invoker<T>
  @SuppressWarnings("unchecked")
  private static <T> Invoker<T> createProxy(
      Invoker<T> delegate, String registryAddress, Class<?> proxyInterface) {
    return (Invoker<T>)
        Proxy.newProxyInstance(
            proxyInterface.getClassLoader(),
            new Class<?>[] {proxyInterface},
            new RegistryCapturingInvoker(delegate, registryAddress));
  }

  @Override
  @Nullable
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    if (method.getDeclaringClass() == Object.class) {
      return invokeObjectMethod(proxy, method, args);
    }
    if (isInvokerInvoke(method)) {
      String previous = DubboRegistryUtil.pushCapturedRegistryAddress(registryAddress);
      try {
        return delegate.invoke((Invocation) args[0]);
      } finally {
        DubboRegistryUtil.restoreCapturedRegistryAddress(previous);
      }
    }
    return invokeDelegate(method, args);
  }

  private static boolean isInvokerInvoke(Method method) {
    return method.getName().equals("invoke")
        && method.getParameterCount() == 1
        && method.getParameterTypes()[0] == Invocation.class;
  }

  @Nullable
  private Object invokeDelegate(Method method, @Nullable Object[] args) throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  // Preserve identity-based Object method behavior. Forwarding equals to a delegate that inherits
  // Object.equals would make proxy.equals(proxy) return false.
  private static Object invokeObjectMethod(Object proxy, Method method, @Nullable Object[] args) {
    String methodName = method.getName();
    if (methodName.equals("equals")) {
      return args != null && proxy == args[0];
    }
    if (methodName.equals("hashCode")) {
      return System.identityHashCode(proxy);
    }
    if (methodName.equals("toString")) {
      return RegistryCapturingInvoker.class.getName()
          + "@"
          + Integer.toHexString(System.identityHashCode(proxy));
    }
    throw new IllegalStateException("Unexpected Object method: " + methodName);
  }
}
