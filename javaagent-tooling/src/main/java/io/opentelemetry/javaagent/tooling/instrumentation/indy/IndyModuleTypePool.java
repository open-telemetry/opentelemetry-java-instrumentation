/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.pool.TypePool;

public class IndyModuleTypePool {

  private IndyModuleTypePool() {}

  /**
   * We implement the TypePool by delegating the .class resource lookup to actual dummy {@link
   * InstrumentationModuleClassLoader} instances. We cache those dummy instances so that repeated
   * calls to {@link #get(ClassLoader, InstrumentationModule)} share the underlying cache for {@link
   * net.bytebuddy.description.type.TypeDescription}s. Note that it is important that the {@link
   * InstrumentationModuleClassLoader} only weakly references the {@link ClassLoader} being
   * instrumented, which is also used as the key of the cache. Otherwise we would accidentally
   * create a strong reference, preventing the instrumented {@link ClassLoader} from being ever
   * GCed. To ensure that the cached {@link InstrumentationModuleClassLoader} is only used for
   * resource lookups and not for classloading, it is wrapped in a {@link ResourceOnlyClassLoader}.
   * Loading classes could otherwise cause the {@link InstrumentationModuleClassLoader} to strongly
   * reference the instrumented {@link ClassLoader}.
   */
  private static final ConcurrentHashMap<
          InstrumentationModule, Cache<ClassLoader, ResourceOnlyClassLoader>>
      classloadersForTypePools = new ConcurrentHashMap<>();

  /**
   * Provides a {@link TypePool} which has the same lookup rules for {@link
   * net.bytebuddy.description.type.TypeDescription}s as {@link InstrumentationModuleClassLoader}
   * have for classes.
   *
   * @param instrumentedCl the classloader being instrumented (e.g. for which the {@link
   *     InstrumentationModuleClassLoader} is being created).
   * @param module the {@link InstrumentationModule} performing the instrumentation
   * @return the type pool
   */
  public static TypePool get(ClassLoader instrumentedCl, InstrumentationModule module) {

    Cache<ClassLoader, ResourceOnlyClassLoader> cacheForModule =
        classloadersForTypePools.computeIfAbsent(module, (k) -> Cache.weak());

    ResourceOnlyClassLoader resourceOnlyCl =
        cacheForModule.computeIfAbsent(
            instrumentedCl,
            cl ->
                new ResourceOnlyClassLoader(
                    IndyModuleRegistry.createInstrumentationModuleClassloader(module, cl)));

    // TODO: this implementation makes the cache for TypeDescriptions somewhat inefficient
    // The returned pool will cache all returned TypeDescriptions as if they were loaded by the
    // dummy InstrumentationModuleClassLoader,
    // even the classes which are actually loaded by the instrumented Classloader or the agent
    // Classloader
    // This could be improved by implementing a custom TypePool instead, which delegates to parent
    // TypePools and mirrors the delegation model of the InstrumentationModuleClassLoader
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(resourceOnlyCl), resourceOnlyCl);
  }

  private static class ResourceOnlyClassLoader extends ClassLoader {

    public ResourceOnlyClassLoader(ClassLoader delegate) {
      super(delegate);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      throw new UnsupportedOperationException(
          "This classloader should only be used for resource loading!");
    }
  }
}
