/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal.injection;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ClassInjector {

  /**
   * Create a builder for a proxy class which will be injected into the instrumented {@link
   * ClassLoader}. The generated proxy will delegate to the original class, which is loaded in a
   * separate classloader.
   *
   * <p>This removes the need for the proxied class and its dependencies to be visible (just like
   * Advices) to the instrumented ClassLoader.
   *
   * @param classToProxy the fully qualified name of the class for which a proxy will be generated
   * @param newProxyName the fully qualified name to use for the generated proxy
   * @return a builder for further customizing the proxy. {@link
   *     ProxyInjectionBuilder#inject(InjectionMode)} must be called to actually inject the proxy.
   */
  ProxyInjectionBuilder proxyBuilder(String classToProxy, String newProxyName);

  /**
   * Same as invoking {@link #proxyBuilder(String, String)}, but the resulting proxy will have the
   * same name as the proxied class.
   *
   * @param classToProxy the fully qualified name of the class for which a proxy will be generated
   * @return a builder for further customizing and injecting the proxy
   */
  default ProxyInjectionBuilder proxyBuilder(String classToProxy) {
    return proxyBuilder(classToProxy, classToProxy);
  }
}
