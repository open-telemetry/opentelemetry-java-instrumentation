/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * This class verifies that a given {@link ClassLoader} satisfies all expectations of a given {@link
 * InstrumentationModule}. It is used to make sure than a module's transformations can be safely
 * applied to a given class loader.
 *
 * @see InstrumentationModule
 * @see ReferenceMatcher
 */
public class ClassLoaderMatcher {

  /**
   * For all {@link InstrumentationModule}s found in the current thread's context classloader calls
   * {@link #matches(InstrumentationModule, ClassLoader, boolean)} and returns the aggregated
   * result.
   *
   * <p>The returned map will be empty if and only if no instrumentation modules were found.
   */
  public static Map<String, List<Mismatch>> matchesAll(
      ClassLoader classLoader, boolean injectHelpers) {
    Map<String, List<Mismatch>> result = new HashMap<>();
    ServiceLoader.load(InstrumentationModule.class)
        .forEach(
            module -> {
              result.put(module.getClass().getName(), matches(module, classLoader, injectHelpers));
            });
    return result;
  }

  /**
   * Returns a list of {@link Mismatch}s between expectations of the given {@link
   * InstrumentationModule} and what the given ClassLoader can provide.
   */
  private static List<Mismatch> matches(
      InstrumentationModule instrumentationModule, ClassLoader classLoader, boolean injectHelpers) {
    List<Mismatch> mismatches = checkReferenceMatcher(instrumentationModule, classLoader);
    mismatches = checkModuleClassLoaderMatcher(instrumentationModule, classLoader, mismatches);
    if (injectHelpers) {
      mismatches = checkHelperInjection(instrumentationModule, classLoader, mismatches);
    }
    return mismatches;
  }

  private static List<Mismatch> checkReferenceMatcher(
      InstrumentationModule instrumentationModule, ClassLoader classLoader) {
    ReferenceMatcher muzzle = ReferenceMatcher.of(instrumentationModule);
    return muzzle.getMismatchedReferenceSources(classLoader);
  }

  private static List<Mismatch> checkModuleClassLoaderMatcher(
      InstrumentationModule instrumentationModule,
      ClassLoader classLoader,
      List<Mismatch> mismatches) {
    if (!instrumentationModule.classLoaderMatcher().matches(classLoader)) {
      mismatches =
          ReferenceMatcher.add(mismatches, new Mismatch.InstrumentationModuleClassLoaderMismatch());
    }
    return mismatches;
  }

  private static List<Mismatch> checkHelperInjection(
      InstrumentationModule instrumentationModule,
      ClassLoader classLoader,
      List<Mismatch> mismatches) {
    try {
      // verify helper injector works
      List<String> allHelperClasses =
          InstrumentationModuleMuzzle.getHelperClassNames(instrumentationModule);
      HelperResourceBuilderImpl helperResourceBuilder = new HelperResourceBuilderImpl();
      instrumentationModule.registerHelperResources(helperResourceBuilder);
      if (!allHelperClasses.isEmpty()) {
        new HelperInjector(
                instrumentationModule.instrumentationName(),
                allHelperClasses,
                helperResourceBuilder.getResources(),
                Thread.currentThread().getContextClassLoader(),
                null)
            .transform(null, null, classLoader, null);
      }
    } catch (RuntimeException e) {
      mismatches = ReferenceMatcher.add(mismatches, new Mismatch.HelperClassesInjectionError());
    }
    return mismatches;
  }

  private ClassLoaderMatcher() {}
}
