/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public class IndyModuleRegistry {

  private IndyModuleRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByName =
      new ConcurrentHashMap<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application
   * classloader. We only store weak references to make sure we don't prevent application
   * classloaders from being GCed. The application classloaders will strongly reference the {@link
   * InstrumentationModuleClassLoader} through the invokedynamic callsites.
   */
  private static final ConcurrentHashMap<
          InstrumentationModule,
          Cache<ClassLoader, WeakReference<InstrumentationModuleClassLoader>>>
      instrumentationClassloaders = new ConcurrentHashMap<>();

  public static InstrumentationModuleClassLoader getInstrumentationClassloader(
      String moduleClassName, ClassLoader instrumentedClassloader) {
    InstrumentationModule instrumentationModule = modulesByName.get(moduleClassName);
    if (instrumentationModule == null) {
      throw new IllegalArgumentException(
          "No module with the class name " + modulesByName + " has been registered!");
    }
    return getInstrumentationClassloader(instrumentationModule, instrumentedClassloader);
  }

  private static synchronized InstrumentationModuleClassLoader getInstrumentationClassloader(
      InstrumentationModule module, ClassLoader instrumentedClassloader) {

    Cache<ClassLoader, WeakReference<InstrumentationModuleClassLoader>> cacheForModule =
        instrumentationClassloaders.computeIfAbsent(module, (k) -> Cache.weak());

    instrumentedClassloader = maskNullClassLoader(instrumentedClassloader);
    WeakReference<InstrumentationModuleClassLoader> cached =
        cacheForModule.get(instrumentedClassloader);
    if (cached != null) {
      InstrumentationModuleClassLoader cachedCl = cached.get();
      if (cachedCl != null) {
        return cachedCl;
      }
    }
    // We can't directly use "compute-if-absent" here because then for a short time only the
    // WeakReference will point to the InstrumentationModuleCL
    InstrumentationModuleClassLoader created =
        createInstrumentationModuleClassloader(module, instrumentedClassloader);
    cacheForModule.put(instrumentedClassloader, new WeakReference<>(created));
    return created;
  }

  private static final ClassLoader BOOT_LOADER = new ClassLoader() {};

  private static ClassLoader maskNullClassLoader(ClassLoader classLoader) {
    return classLoader == null ? BOOT_LOADER : classLoader;
  }

  static InstrumentationModuleClassLoader createInstrumentationModuleClassloader(
      InstrumentationModule module, ClassLoader instrumentedClassloader) {
    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader moduleCl =
        new InstrumentationModuleClassLoader(instrumentedClassloader, agentOrExtensionCl);
    moduleCl.installModule(module);
    return moduleCl;
  }

  public static void registerIndyModule(InstrumentationModule module) {
    if (!module.isIndyModule()) {
      throw new IllegalArgumentException("Provided module is not an indy module!");
    }
    String moduleName = module.getClass().getName();
    if (modulesByName.putIfAbsent(moduleName, module) != null) {
      throw new IllegalArgumentException(
          "A module with the class name " + moduleName + " has already been registered!");
    }
  }
}
