/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptySet;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

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
   * For all {@link InstrumentationModule}s found in the Muzzle tooling's class loader calls {@link
   * #matches(InstrumentationModule, ClassLoader, boolean)} and returns the aggregated result.
   *
   * <p>The returned map will be empty if no instrumentation modules remain after exclusions.
   */
  public static Map<String, List<Mismatch>> matchesAll(
      ClassLoader classLoader, boolean injectHelpers, Set<String> excludedInstrumentationNames) {
    return matchesAll(classLoader, injectHelpers, excludedInstrumentationNames, emptySet());
  }

  /**
   * Matches all discovered modules except those excluded by public enablement name or fully
   * qualified module class name. Unknown module class names are rejected.
   */
  public static Map<String, List<Mismatch>> matchesAll(
      ClassLoader classLoader,
      boolean injectHelpers,
      Set<String> excludedInstrumentationNames,
      Set<String> excludedInstrumentationModules) {
    disableMatcherCache();

    Map<String, List<Mismatch>> result = new HashMap<>();
    for (InstrumentationModule module :
        selectModules(
            ServiceLoader.load(
                InstrumentationModule.class, ClassLoaderMatcher.class.getClassLoader()),
            excludedInstrumentationNames,
            excludedInstrumentationModules)) {
      result.put(module.getClass().getName(), matches(module, classLoader, injectHelpers));
    }
    return result;
  }

  // visible for testing
  static List<InstrumentationModule> selectModules(
      Iterable<InstrumentationModule> modules,
      Set<String> excludedInstrumentationNames,
      Set<String> excludedInstrumentationModules) {
    Set<String> unknownModules = new TreeSet<>(excludedInstrumentationModules);
    List<InstrumentationModule> selected = new ArrayList<>();
    for (InstrumentationModule module : modules) {
      String className = module.getClass().getName();
      unknownModules.remove(className);
      if (!excludedInstrumentationModules.contains(className)
          && module.instrumentationNames().stream()
              .noneMatch(excludedInstrumentationNames::contains)) {
        selected.add(module);
      }
    }
    if (!unknownModules.isEmpty()) {
      throw new IllegalArgumentException(
          "Excluded InstrumentationModule classes were not found: " + unknownModules);
    }
    return selected;
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
                ClassLoaderMatcher.class.getClassLoader(),
                null)
            .transform(null, null, classLoader, null, null);
      }
    } catch (RuntimeException e) {
      mismatches = ReferenceMatcher.add(mismatches, new Mismatch.HelperClassesInjectionError());
    }
    return mismatches;
  }

  private static void disableMatcherCache() {
    try {
      Class<?> matcherClass =
          Class.forName(
              "io.opentelemetry.javaagent.extension.matcher.ClassLoaderHasClassesNamedMatcher");
      Field field = matcherClass.getDeclaredField("useCache");
      field.setAccessible(true);
      field.setBoolean(null, false);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to disable cache for ClassLoaderHasClassesNamedMatcher", e);
    }
  }

  private ClassLoaderMatcher() {}
}
