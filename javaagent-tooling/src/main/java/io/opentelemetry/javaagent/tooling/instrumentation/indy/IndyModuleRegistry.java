/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.tooling.ModuleOpener;
import io.opentelemetry.javaagent.tooling.util.ClassLoaderValue;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;

public class IndyModuleRegistry {

  private IndyModuleRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByClassName =
      new ConcurrentHashMap<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application class
   * loader. The {@link InstrumentationModuleClassLoader} are kept alive by a strong reference from
   * the instrumented class loader realized via {@link ClassLoaderValue}.
   *
   * <p>The keys of the contained map are the instrumentation module group names, see {@link
   * ExperimentalInstrumentationModule#getModuleGroup()};
   */
  private static final ClassLoaderValue<Map<String, InstrumentationModuleClassLoader>>
      instrumentationClassLoaders = new ClassLoaderValue<>();

  public static InstrumentationModuleClassLoader getInstrumentationClassLoader(
      String moduleClassName, ClassLoader instrumentedClassLoader) {
    InstrumentationModule instrumentationModule = modulesByClassName.get(moduleClassName);
    if (instrumentationModule == null) {
      throw new IllegalArgumentException(
          "No module with the class name " + modulesByClassName + " has been registered!");
    }
    return getInstrumentationClassLoader(instrumentationModule, instrumentedClassLoader);
  }

  public static InstrumentationModuleClassLoader getInstrumentationClassLoader(
      InstrumentationModule module, ClassLoader instrumentedClassLoader) {

    String groupName = getModuleGroup(module);

    Map<String, InstrumentationModuleClassLoader> loadersByGroupName =
        instrumentationClassLoaders.get(instrumentedClassLoader);

    if (loadersByGroupName == null) {
      throw new IllegalArgumentException(
          module
              + " has not been initialized for class loader "
              + instrumentedClassLoader
              + " yet");
    }

    InstrumentationModuleClassLoader loader = loadersByGroupName.get(groupName);
    if (loader == null || !loader.hasModuleInstalled(module)) {
      throw new IllegalArgumentException(
          module
              + " has not been initialized for class loader "
              + instrumentedClassLoader
              + " yet");
    }

    if (module instanceof ExperimentalInstrumentationModule) {
      ExperimentalInstrumentationModule experimentalModule =
          (ExperimentalInstrumentationModule) module;

      Instrumentation instrumentation = InstrumentationHolder.getInstrumentation();
      if (instrumentation == null) {
        throw new IllegalStateException("global instrumentation not available");
      }

      if (JavaModule.isSupported()) {
        // module opener only usable for java 9+
        experimentalModule
            .jpmsModulesToOpen()
            .forEach(
                (javaModule, packages) ->
                    ModuleOpener.open(instrumentation, javaModule, loader, packages));
      }
    }
    return loader;
  }

  /**
   * Returns a newly created class loader containing only the provided module. Note that other
   * modules from the same module group (see {@link #getModuleGroup(InstrumentationModule)}) will
   * not be installed in this class loader.
   */
  public static InstrumentationModuleClassLoader
      createInstrumentationClassLoaderWithoutRegistration(
          InstrumentationModule module, ClassLoader instrumentedClassLoader) {
    // TODO: remove this method and replace usages with a custom TypePool implementation instead
    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader cl =
        new InstrumentationModuleClassLoader(instrumentedClassLoader, agentOrExtensionCl);
    cl.installModule(module);
    return cl;
  }

  public static AgentBuilder.Identified.Extendable initializeModuleLoaderOnMatch(
      InstrumentationModule module, AgentBuilder.Identified.Extendable agentBuilder) {
    if (!module.isIndyModule()) {
      throw new IllegalArgumentException("Provided module is not an indy module!");
    }
    String moduleName = module.getClass().getName();
    InstrumentationModule existingRegistration = modulesByClassName.putIfAbsent(moduleName, module);
    if (existingRegistration != null && existingRegistration != module) {
      throw new IllegalArgumentException(
          "A different module with the class name " + moduleName + " has already been registered!");
    }
    return agentBuilder.transform(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
          initializeModuleLoaderForClassLoader(module, classLoader);
          return builder;
        });
  }

  private static void initializeModuleLoaderForClassLoader(
      InstrumentationModule module, ClassLoader classLoader) {

    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();

    String groupName = getModuleGroup(module);

    InstrumentationModuleClassLoader moduleCl =
        instrumentationClassLoaders
            .computeIfAbsent(classLoader, ConcurrentHashMap::new)
            .computeIfAbsent(
                groupName,
                unused -> new InstrumentationModuleClassLoader(classLoader, agentOrExtensionCl));

    moduleCl.installModule(module);
  }

  private static String getModuleGroup(InstrumentationModule module) {
    if (module instanceof ExperimentalInstrumentationModule) {
      return ((ExperimentalInstrumentationModule) module).getModuleGroup();
    }
    return module.getClass().getName();
  }
}
