/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.tooling.ExtensionClassLoader;
import io.opentelemetry.javaagent.tooling.ModuleOpener;
import io.opentelemetry.javaagent.tooling.util.ClassLoaderValue;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;

public class IndyModuleRegistry {

  private IndyModuleRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByClassName =
      new ConcurrentHashMap<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application class
   * loader for the internal instrumentation. The {@link InstrumentationModuleClassLoader} are kept
   * alive by a strong reference from the instrumented class loader realized via {@link
   * ClassLoaderValue}.
   */
  private static final ClassLoaderValue<InstrumentationModuleClassLoader>
      instrumentationClassLoaders = new ClassLoaderValue<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given extension class
   * loader with application class loader as key. The {@link InstrumentationModuleClassLoader} are
   * kept alive by a strong reference from the instrumented class loader realized via {@link
   * ClassLoaderValue}.
   */
  private static final ClassLoaderValue<Map<ClassLoader, InstrumentationModuleClassLoader>>
      extensionsInstrumentationClassLoaders = new ClassLoaderValue<>();

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

    ClassLoader moduleCl = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader loader =
        lookupInstrumentationClassLoader(instrumentedClassLoader, moduleCl);

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

  @Nullable
  private static InstrumentationModuleClassLoader lookupInstrumentationClassLoader(
      ClassLoader instrumentedClassLoader, ClassLoader moduleCl) {
    if (!(moduleCl instanceof ExtensionClassLoader)) {
      // internal instrumentation is using one CL per instrumented CL.
      return instrumentationClassLoaders.get(instrumentedClassLoader);
    }
    // extension module needs to use a common CL per extension and instrumented CL.
    Map<ClassLoader, InstrumentationModuleClassLoader> map =
        extensionsInstrumentationClassLoaders.get(moduleCl);
    if (map != null) {
      return map.get(instrumentedClassLoader);
    }
    return null;
  }

  /**
   * Returns a newly created class loader containing only the provided module. Note that other
   * modules from the same module group will not be installed in this class loader.
   */
  public static InstrumentationModuleClassLoader createInstrumentationClassLoaderForMuzzle(
      InstrumentationModule module, ClassLoader instrumentedClassLoader) {
    // TODO: remove this method and replace usages with a custom TypePool implementation instead
    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader cl =
        new InstrumentationModuleClassLoader(instrumentedClassLoader, agentOrExtensionCl);
    cl.installModule(module, true);
    return cl;
  }

  public static AgentBuilder.Identified.Extendable initializeModuleLoaderOnMatch(
      InstrumentationModule module, AgentBuilder.Identified.Extendable agentBuilder) {
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
    InstrumentationModuleClassLoader moduleCl;
    if (!(agentOrExtensionCl instanceof ExtensionClassLoader)) {
      // non-extension modules are loaded in a common InstrumentationModuleClassLoader per
      // instrumented CL
      moduleCl =
          instrumentationClassLoaders.computeIfAbsent(
              classLoader,
              () -> new InstrumentationModuleClassLoader(classLoader, agentOrExtensionCl));
    } else {
      // extension modules are loaded in a common InstrumentationModuleCLassLoader per extension and
      // instrumented CL
      moduleCl =
          extensionsInstrumentationClassLoaders
              .computeIfAbsent(agentOrExtensionCl, HashMap::new)
              .computeIfAbsent(
                  classLoader,
                  k -> new InstrumentationModuleClassLoader(classLoader, agentOrExtensionCl));
    }

    moduleCl.installModule(module);
  }
}
