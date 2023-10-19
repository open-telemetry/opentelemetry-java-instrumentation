/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

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

    Set<String> toInject = new HashSet<>(InstrumentationModuleMuzzle.getHelperClassNames(module));
    // TODO (Jonas): Make muzzle include advice classes as helper classes
    // so that we don't have to include them here
    toInject.addAll(getModuleAdviceNames(module));

    ClassLoader agentOrExtensionCl = module.getClass().getClassLoader();
    Map<String, ClassCopySource> injectedClasses =
        toInject.stream()
            .collect(
                Collectors.toMap(
                    name -> name, name -> ClassCopySource.create(name, agentOrExtensionCl)));

    return new InstrumentationModuleClassLoader(
        instrumentedClassloader, agentOrExtensionCl, injectedClasses);
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

  private static Set<String> getModuleAdviceNames(InstrumentationModule module) {
    Set<String> adviceNames = new HashSet<>();
    TypeTransformer nameCollector =
        new TypeTransformer() {
          @Override
          public void applyAdviceToMethod(
              ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
            adviceNames.add(adviceClassName);
          }

          @Override
          public void applyTransformer(AgentBuilder.Transformer transformer) {}
        };
    for (TypeInstrumentation instr : module.typeInstrumentations()) {
      instr.transform(nameCollector);
    }
    return adviceNames;
  }
}
