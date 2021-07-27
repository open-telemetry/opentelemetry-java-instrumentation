/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * This class verifies that a given {@link ClassLoader} satisfies all expectations of a given {@link
 * InstrumentationModule}. It is used to make sure than module's transformations can be safely
 * applied to a given class loader.
 *
 * @see InstrumentationModule
 * @see ReferenceMatcher
 */
public class ClassLoaderMatcher {

  /**
   * For all {@link InstrumentationModule}s found in the current thread's context classloader calls
   * {@link #matches(InstrumentationModule, ClassLoader)} and returns the aggregated result.
   *
   * <p>The returned map will be empty if and only if no instrumentation modules were found.
   */
  public static Map<String, List<Mismatch>> matchesAll(ClassLoader classLoader) {
    Map<String, List<Mismatch>> result = new HashMap<>();
    ServiceLoader.load(InstrumentationModule.class)
        .forEach(
            module -> {
              result.put(module.getClass().getName(), matches(module, classLoader));
            });
    return result;
  }

  /**
   * Returns a list of {@link Mismatch}s between expectations of the given {@link
   * InstrumentationModule} and what the given ClassLoader can provide.
   */
  public static List<Mismatch> matches(
      InstrumentationModule instrumentationModule, ClassLoader classLoader) {
    ReferenceMatcher muzzle =
        new ReferenceMatcher(
            instrumentationModule.getMuzzleHelperClassNames(),
            instrumentationModule.getMuzzleReferences(),
            instrumentationModule::isHelperClass);
    List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(classLoader);

    if (!instrumentationModule.classLoaderMatcher().matches(classLoader)) {
      mismatches =
          ReferenceMatcher.lazyAdd(
              mismatches, new Mismatch.InstrumentationModuleClassLoaderMismatch());
    }

    return mismatches;
  }

  private ClassLoaderMatcher() {}
}
