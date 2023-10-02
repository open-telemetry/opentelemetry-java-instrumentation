/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ProxyInjectionBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

public class ClassInjectorImpl implements ClassInjector {

  private final InstrumentationModule instrumentationModule;

  private final Map<String, Function<ClassLoader, byte[]>> classesToInject;

  private final IndyProxyFactory proxyFactory;

  public ClassInjectorImpl(InstrumentationModule module) {
    instrumentationModule = module;
    classesToInject = new HashMap<>();
    proxyFactory = IndyBootstrap.getProxyFactory(module);
  }

  public Map<String, Function<ClassLoader, byte[]>> getClassesToInject() {
    return classesToInject;
  }

  @Override
  public ProxyInjectionBuilder proxyBuilder(String classToProxy, String newProxyName) {
    return new ProxyBuilder(classToProxy, newProxyName);
  }

  private class ProxyBuilder implements ProxyInjectionBuilder {

    private final String classToProxy;
    private final String proxyClassName;

    ProxyBuilder(String classToProxy, String proxyClassName) {
      this.classToProxy = classToProxy;
      this.proxyClassName = proxyClassName;
    }

    @Override
    public void inject(InjectionMode mode) {
      if (mode != InjectionMode.CLASS_ONLY) {
        throw new UnsupportedOperationException("Not yet implemented");
      }
      classesToInject.put(
          proxyClassName,
          cl -> {
            TypePool typePool = IndyModuleTypePool.get(cl, instrumentationModule);
            TypeDescription proxiedType = typePool.describe(classToProxy).resolve();
            return proxyFactory.generateProxy(proxiedType, proxyClassName).getBytes();
          });
    }
  }
}
