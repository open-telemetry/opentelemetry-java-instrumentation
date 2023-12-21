/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.opentelemetry.javaagent.tooling.util.ClassLoaderValue;
import net.bytebuddy.agent.builder.AgentBuilder;

public class IndyModuleRegistry {

  private IndyModuleRegistry() {}

  private static final ConcurrentHashMap<String, InstrumentationModule> modulesByClassName =
      new ConcurrentHashMap<>();

  /**
   * Weakly references the {@link InstrumentationModuleClassLoader}s for a given application
   * classloader. The {@link InstrumentationModuleClassLoader} are kept alive by a strong reference
   * from the instrumented classloader realized via {@link ClassLoaderValue}.
   *
   * <p>The keys of the contained map are the instrumentation module group names, see {@link
   * ExperimentalInstrumentationModule#getModuleGroup()};
   */
  private static final ClassLoaderValue<Map<String, InstrumentationModuleClassLoader>> instrumentationClassloaders = new ClassLoaderValue<>();


  public static InstrumentationModuleClassLoader getInstrumentationClassloader(
      String moduleClassName, ClassLoader instrumentedClassloader) {
    InstrumentationModule instrumentationModule = modulesByClassName.get(moduleClassName);
    if (instrumentationModule == null) {
      throw new IllegalArgumentException(
          "No module with the class name " + modulesByClassName + " has been registered!");
    }
    return getInstrumentationClassloader(instrumentationModule, instrumentedClassloader);
  }

  public static InstrumentationModuleClassLoader getInstrumentationClassloader(
      InstrumentationModule module, ClassLoader instrumentedClassloader) {

    String groupName = getModuleGroup(module);

    Map<String, InstrumentationModuleClassLoader> loadersByGroupName =
        instrumentationClassloaders.get(instrumentedClassloader);

    if (loadersByGroupName == null) {
      throw new IllegalArgumentException(module + " has not been initialized for classloader " + instrumentedClassloader + " yet");
    }

    InstrumentationModuleClassLoader loader = loadersByGroupName.get(groupName);
    if (loader == null || !loader.hasModuleInstalled(module)) {
      throw new IllegalArgumentException(module + " has not been initialized for classloader " + instrumentedClassloader + " yet");
    }

    return loader;
  }

  /**
   * Returns a newly created classloader containing only the provided module.
   * Note that other modules from the same module group (see {@link #getModuleGroup(InstrumentationModule)})
   * will not be installed in this classloader.
   */
  public static InstrumentationModuleClassLoader
      createInstrumentationClassloaderWithoutRegistration(
          InstrumentationModule module, ClassLoader instrumentedClassloader) {
    // TODO: remove this method and replace usages with a custom TypePool implementation instead
    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();
    InstrumentationModuleClassLoader cl = new InstrumentationModuleClassLoader(instrumentedClassloader, agentOrExtensionCl);
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
          initializeModuleLoaderForClassloader(module, classLoader);
          return builder;
        });
  }

  private static void initializeModuleLoaderForClassloader(
      InstrumentationModule module, ClassLoader classLoader) {

    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();

    String groupName = getModuleGroup(module);

    InstrumentationModuleClassLoader moduleCl = instrumentationClassloaders
        .computeIfAbsent(classLoader, ConcurrentHashMap::new)
        .computeIfAbsent(groupName, unused -> new InstrumentationModuleClassLoader(classLoader, agentOrExtensionCl));

    moduleCl.installModule(module);
  }

  private static String getModuleGroup(InstrumentationModule module) {
    if (module instanceof ExperimentalInstrumentationModule) {
      return ((ExperimentalInstrumentationModule) module).getModuleGroup();
    }
    return module.getClass().getName();
  }
}
