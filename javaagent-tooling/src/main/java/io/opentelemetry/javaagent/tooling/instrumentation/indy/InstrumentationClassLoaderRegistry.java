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
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;

public class InstrumentationClassLoaderRegistry {

  private InstrumentationClassLoaderRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByClassName =
      new ConcurrentHashMap<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application class
   * loader. For internal instrumentation, the agent classloader key is used, for extensions the key
   * is the extension classloader. <br>
   * The {@link InstrumentationModuleClassLoader} are kept alive by a strong reference from the
   * instrumented class loader realized via {@link ClassLoaderValue}.
   */
  private static final ClassLoaderValue<Map<ClassLoader, InstrumentationModuleClassLoader>>
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

    ClassLoader agentOrExtensionClassLoader = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader instrumentationClassLoader =
        lookupInstrumentationClassLoader(instrumentedClassLoader, agentOrExtensionClassLoader);

    if (instrumentationClassLoader == null
        || !instrumentationClassLoader.hasModuleInstalled(module)) {
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
                    ModuleOpener.open(
                        instrumentation, javaModule, instrumentationClassLoader, packages));
      }
    }
    return instrumentationClassLoader;
  }

  @Nullable
  private static InstrumentationModuleClassLoader lookupInstrumentationClassLoader(
      ClassLoader instrumentedClassLoader, ClassLoader agentOrExtensionClassLoader) {
    Map<ClassLoader, InstrumentationModuleClassLoader> map =
        instrumentationClassLoaders.get(instrumentedClassLoader);
    if (map == null) {
      return null;
    }
    return map.get(agentOrExtensionClassLoader);
  }

  /** Returns a newly created class loader containing only the provided module for muzzle. */
  public static InstrumentationModuleClassLoader createInstrumentationClassLoaderForMuzzle(
      InstrumentationModule module, ClassLoader instrumentedClassLoader) {
    // TODO: remove this method and replace usages with a custom TypePool implementation instead
    ClassLoader agentOrExtensionClassLoader = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader instrumentationClassLoader =
        new InstrumentationModuleClassLoader(instrumentedClassLoader, agentOrExtensionClassLoader);
    instrumentationClassLoader.installModule(module, true);
    return instrumentationClassLoader;
  }

  public static AgentBuilder.Identified.Extendable initializeModuleClassLoaderOnTransform(
      InstrumentationModule module, AgentBuilder.Identified.Extendable agentBuilder) {
    String moduleClassName = module.getClass().getName();
    InstrumentationModule existingRegistration =
        modulesByClassName.putIfAbsent(moduleClassName, module);
    if (existingRegistration != null && existingRegistration != module) {
      throw new IllegalArgumentException(
          "A different module with the class name "
              + moduleClassName
              + " has already been registered!");
    }
    return agentBuilder.transform(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
          initializeModuleLoaderForClassLoader(module, classLoader);
          return builder;
        });
  }

  private static void initializeModuleLoaderForClassLoader(
      InstrumentationModule module, ClassLoader classLoader) {

    ClassLoader agentOrExtensionClassLoader = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader instrumentationClassLoader;

    // Because extensions have their own classloader, extension modules are loaded in a common
    // InstrumentationModuleCLassLoader per extension and instrumented CL (with extension CL as key)
    // Non-extension modules are loaded in a common InstrumentationModuleClassLoader per
    // instrumented CL (with agent CL as key)

    instrumentationClassLoader =
        instrumentationClassLoaders
            .computeIfAbsent(classLoader, ConcurrentHashMap::new)
            .computeIfAbsent(
                agentOrExtensionClassLoader,
                k ->
                    new InstrumentationModuleClassLoader(classLoader, agentOrExtensionClassLoader));

    instrumentationClassLoader.installModule(module);
  }
}
