/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ProxyInjectionBuilder;
import io.opentelemetry.javaagent.tooling.HelperClassDefinition;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;

public class ClassInjectorImpl implements ClassInjector {

  private final InstrumentationModule instrumentationModule;

  private final List<Function<ClassLoader, HelperClassDefinition>> classesToInject;

  private final IndyProxyFactory proxyFactory;

  public ClassInjectorImpl(InstrumentationModule module) {
    instrumentationModule = module;
    classesToInject = new ArrayList<>();
    proxyFactory = IndyBootstrap.getProxyFactory(module);
  }

  public List<HelperClassDefinition> getClassesToInject(ClassLoader instrumentedCl) {
    return classesToInject.stream()
        .map(generator -> generator.apply(instrumentedCl))
        .collect(Collectors.toList());
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
      classesToInject.add(
          cl -> {
            InstrumentationModuleClassLoader moduleCl =
                IndyModuleRegistry.getInstrumentationClassLoader(instrumentationModule, cl);
            TypePool typePool =
                AgentTooling.poolStrategy()
                    .typePool(AgentTooling.locationStrategy().classFileLocator(moduleCl), moduleCl);
            TypeDescription proxiedType = typePool.describe(classToProxy).resolve();
            DynamicType.Unloaded<?> proxy = proxyFactory.generateProxy(proxiedType, proxyClassName);
            return HelperClassDefinition.create(proxy, mode);
          });
    }
  }
}
