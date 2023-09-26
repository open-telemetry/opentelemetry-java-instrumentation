package io.opentelemetry.javaagent.tooling.instrumentation.indy.injection;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.injection.InjectionMode;
import io.opentelemetry.javaagent.extension.instrumentation.injection.ProxyInjectionBuilder;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyBootstrap;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleTypePool;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyProxyFactory;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class IndyClassInjector implements ClassInjector {

  /**
   * The CL which loads the {@link io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule}.
   */
  private final InstrumentationModule instrumentationModule;

  private final Map<String, Function<ClassLoader, byte[]>> classesToInject;

  private final IndyProxyFactory proxyFactory;

  public IndyClassInjector(InstrumentationModule module) {
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
      if(mode != InjectionMode.CLASS_ONLY) {
        throw new UnsupportedOperationException("Not yet implemented");
      }
      classesToInject.put(proxyClassName, cl -> {
        TypePool typePool = IndyModuleTypePool.get(cl, instrumentationModule);
        TypeDescription proxiedType = typePool.describe(classToProxy).resolve();
        return proxyFactory.generateProxy(proxiedType, proxyClassName).getBytes();
      });
    }
  }
}
