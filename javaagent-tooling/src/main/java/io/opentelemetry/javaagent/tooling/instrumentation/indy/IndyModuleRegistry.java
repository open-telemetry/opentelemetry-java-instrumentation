/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.agent.builder.AgentBuilder;

public class IndyModuleRegistry {

  private IndyModuleRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByName =
      new ConcurrentHashMap<>();

  private static class ModuleRegistration {

    private final WeakReference<ClassLoader> instrumentedCl;

    private final Set<InstrumentationModule> registeredModules =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    private WeakReference<InstrumentationModuleClassLoader> modulesClassloader = null;

    private ModuleRegistration(ClassLoader instrumentedCl) {
      this.instrumentedCl = new WeakReference<>(instrumentedCl);
    }

    private boolean isRegistered(InstrumentationModule module) {
      return registeredModules.contains(module);
    }

    public void register(InstrumentationModule module) {
      if (registeredModules.contains(module)) {
        return;
      }
      synchronized (this) {
        if (registeredModules.add(module)) {
          if (modulesClassloader != null) {
            InstrumentationModuleClassLoader modulesCl = modulesClassloader.get();
            if (modulesCl != null) {
              // Classloader has already been created, at the module to it
              modulesCl.installModule(module);
            }
          }
        }
      }
    }

    public InstrumentationModuleClassLoader getOrCreateModulesClassloader() {
      if (modulesClassloader != null) {
        InstrumentationModuleClassLoader modulesCl = modulesClassloader.get();
        if (modulesCl != null) {
          return modulesCl;
        }
      }
      synchronized (this) {
        if (modulesClassloader != null) {
          InstrumentationModuleClassLoader modulesCl = modulesClassloader.get();
          if (modulesCl != null) {
            return modulesCl;
          }
        }
        ClassLoader instrumented = instrumentedCl.get();
        if (instrumented == null) {
          throw new IllegalStateException("Target classloader has been GCed!");
        }
        InstrumentationModuleClassLoader modulesCl =
            createInstrumentationModuleClassloader(registeredModules, instrumented);
        modulesClassloader = new WeakReference<>(modulesCl);
        return modulesCl;
      }
    }
  }

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application
   * classloader. We only store weak references to make sure we don't prevent application
   * classloaders from being GCed. The application classloaders will strongly reference the {@link
   * InstrumentationModuleClassLoader} through the invokedynamic callsites.
   *
   * <p>The keys of this map are the instrumentation module group names, see {@link
   * ExperimentalInstrumentationModule#getModuleGroup()};
   */
  private static final ConcurrentHashMap<String, Cache<ClassLoader, ModuleRegistration>>
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

    String groupName = getModuleGroup(module);
    Cache<ClassLoader, ModuleRegistration> cacheForGroup =
        instrumentationClassloaders.get(groupName);
    if (cacheForGroup == null) {
      throw new IllegalArgumentException(module + " has not been registered yet");
    }

    instrumentedClassloader = maskNullClassLoader(instrumentedClassloader);
    ModuleRegistration registrations = cacheForGroup.get(instrumentedClassloader);
    if (registrations == null || !registrations.isRegistered(module)) {
      throw new IllegalArgumentException(
          module + " has not been registered for Classloader " + instrumentedClassloader);
    }
    return registrations.getOrCreateModulesClassloader();
  }

  /**
   * Returns a classloader which behaves exactly as if you would first register this module and
   * afterwards call {@link #getInstrumentationClassloader(String, ClassLoader)}.
   *
   * <p>The difference is that the registration actually does not happen, therefore this call is
   * side-effect free.
   *
   * <p>The returned classloader will load the provided module AND all other previously registered
   * for the given instrumentedClassloader instrumentation modules with the same group.
   */
  public static InstrumentationModuleClassLoader
      createInstrumentationClassloaderWithoutRegistration(
          InstrumentationModule module, ClassLoader instrumentedClassloader) {
    // TODO: remove this method and replace usages with a custom TypePool implementation instead
    String groupName = getModuleGroup(module);

    instrumentedClassloader = maskNullClassLoader(instrumentedClassloader);

    Set<InstrumentationModule> modules = new HashSet<>();
    modules.add(module);
    Cache<ClassLoader, ModuleRegistration> cacheForGroup =
        instrumentationClassloaders.get(groupName);
    if (cacheForGroup != null) {
      ModuleRegistration registration = cacheForGroup.get(instrumentedClassloader);
      if (registration != null) {
        modules.addAll(registration.registeredModules);
      }
    }

    return createInstrumentationModuleClassloader(modules, instrumentedClassloader);
  }

  private static final ClassLoader BOOT_LOADER = new ClassLoader(null) {};

  private static ClassLoader maskNullClassLoader(ClassLoader classLoader) {
    return classLoader == null ? BOOT_LOADER : classLoader;
  }

  static InstrumentationModuleClassLoader createInstrumentationModuleClassloader(
      Set<InstrumentationModule> modules, ClassLoader instrumentedClassloader) {
    if (modules.isEmpty()) {
      throw new IllegalArgumentException("Must provide at least one module!");
    }
    ClassLoader agentOrExtensionCl = modules.iterator().next().getClass().getClassLoader();
    InstrumentationModuleClassLoader moduleCl =
        new InstrumentationModuleClassLoader(instrumentedClassloader, agentOrExtensionCl);

    modules.forEach(moduleCl::installModule);
    return moduleCl;
  }

  public static AgentBuilder.Identified.Extendable registerModuleOnMatch(
      InstrumentationModule module, AgentBuilder.Identified.Extendable agentBuilder) {
    if (!module.isIndyModule()) {
      throw new IllegalArgumentException("Provided module is not an indy module!");
    }
    String moduleName = module.getClass().getName();
    InstrumentationModule existingRegistration = modulesByName.putIfAbsent(moduleName, module);
    if (existingRegistration != null && existingRegistration != module) {
      throw new IllegalArgumentException(
          "A different module with the class name " + moduleName + " has already been registered!");
    }
    return agentBuilder.transform(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
          // this causes the classloader to be created and kept alive when the first class matches
          registerModuleForClassloader(module, classLoader);
          return builder;
        });
  }

  private static void registerModuleForClassloader(
      InstrumentationModule module, ClassLoader classLoader) {
    String groupName = getModuleGroup(module);
    Cache<ClassLoader, ModuleRegistration> cacheForGroup =
        instrumentationClassloaders.computeIfAbsent(groupName, (k) -> Cache.weak());
    classLoader = maskNullClassLoader(classLoader);
    ModuleRegistration registrations =
        cacheForGroup.computeIfAbsent(classLoader, ModuleRegistration::new);
    registrations.register(module);
  }

  private static String getModuleGroup(InstrumentationModule module) {
    if (module instanceof ExperimentalInstrumentationModule) {
      return ((ExperimentalInstrumentationModule) module).getModuleGroup();
    }
    return module.getClass().getName();
  }
}
