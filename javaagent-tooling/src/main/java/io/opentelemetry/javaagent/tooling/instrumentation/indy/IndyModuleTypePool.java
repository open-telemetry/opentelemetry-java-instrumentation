package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import net.bytebuddy.pool.TypePool;
import java.util.concurrent.ConcurrentHashMap;

public class IndyModuleTypePool {

  private IndyModuleTypePool() {}

  private static final ConcurrentHashMap<
      InstrumentationModule,
      Cache<ClassLoader, ResourceOnlyClassLoader>>
      classloadersForTypePools = new ConcurrentHashMap<>();


  public static TypePool get(ClassLoader instrumentedCl, InstrumentationModule module) {

    Cache<ClassLoader, ResourceOnlyClassLoader> cacheForModule =
        classloadersForTypePools.computeIfAbsent(module, (k) -> Cache.weak());

    ResourceOnlyClassLoader resourceOnlyCl = cacheForModule.computeIfAbsent(
        instrumentedCl, cl -> new ResourceOnlyClassLoader(IndyModuleRegistry.createInstrumentationModuleClassloader(module, cl)));

    return AgentTooling.poolStrategy().typePool(AgentTooling.locationStrategy().classFileLocator(resourceOnlyCl), resourceOnlyCl);
  }

  private static class ResourceOnlyClassLoader extends ClassLoader {

    public ResourceOnlyClassLoader(ClassLoader delegate) {
      super(delegate);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      throw new UnsupportedOperationException("This classloader should only be used for resource loading!");
    }
  }
}
