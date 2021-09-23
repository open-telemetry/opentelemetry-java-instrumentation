/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.matcher;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the muzzle gradle plugin.
 *
 * <p>In order to understand this class and its weirdness, one has to remember that there are three
 * different independent class loaders at play here.
 *
 * <p>First, Gradle class loader that has loaded the muzzle-check plugin that calls this class. This
 * one has a lot of Gradle specific stuff and we don't want it to be available during muzzle checks.
 *
 * <p>Second, there is agent or instrumentation class loader, which contains all
 * InstrumentationModules and helper classes. The actual muzzle check process happens "inside" that
 * class loader. This means that we load {@link
 * io.opentelemetry.javaagent.tooling.muzzle.ClassLoaderMatcher} from it and we allow it to find all
 * InstrumentationModules from agent class loader.
 *
 * <p>Finally, there is user class loader. It contains the specific version of the instrumented
 * library that we want to muzzle-check: "does this version provide all the expected hooks and
 * classes and methods that our instrumentations expect".
 */

// TODO the next line is not true anymore. Switch from System.err to Gradle logger.
// Runs in special classloader so tedious to provide access to the Gradle logger.
@SuppressWarnings("SystemOut")
public final class MuzzleGradlePluginUtil {
  private static final String INDENT = "  ";

  /**
   * Verifies that all instrumentations present in the {@code agentClassLoader} can be safely
   * applied to the passed {@code userClassLoader}.
   *
   * <p>This method throws whenever one of the following step fails (and {@code assertPass} is
   * true):
   *
   * <ol>
   *   <li>{@code userClassLoader} is not matched by the {@link
   *       InstrumentationModule#classLoaderMatcher()} method
   *   <li>{@link ReferenceMatcher} of any instrumentation module finds any mismatch
   *   <li>any helper class defined in {@link InstrumentationModule#getMuzzleHelperClassNames()}
   *       fails to be injected into {@code userClassLoader}
   * </ol>
   *
   * <p>When {@code assertPass = false} this method behaves in an opposite way: failure in any of
   * the first two steps is expected (helper classes are not injected at all).
   *
   * <p>This method is repeatedly called by the {@code :muzzle} gradle task - each tested dependency
   * version passes different {@code userClassLoader}.
   */
  public static void assertInstrumentationMuzzled(
      ClassLoader agentClassLoader, ClassLoader userClassLoader, boolean assertPass)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {

    Class<?> matcherClass =
        agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.muzzle.ClassLoaderMatcher");

    // We cannot reference Mismatch class directly here, because we are loaded from a differen
    // classloader.
    Map<String, List<Object>> allMismatches =
        (Map<String, List<Object>>)
            matcherClass
                .getMethod("matchesAll", ClassLoader.class, boolean.class)
                .invoke(null, userClassLoader, assertPass);

    allMismatches.forEach(
        (moduleName, mismatches) -> {
          boolean passed = mismatches.isEmpty();

          if (passed && !assertPass) {
            System.err.println("MUZZLE PASSED " + moduleName + " BUT FAILURE WAS EXPECTED");
            throw new IllegalStateException(
                "Instrumentation unexpectedly passed Muzzle validation");
          } else if (!passed && assertPass) {
            System.err.println("FAILED MUZZLE VALIDATION: " + moduleName + " mismatches:");

            for (Object mismatch : mismatches) {
              System.err.println("-- " + mismatch);
            }
            throw new IllegalStateException("Instrumentation failed Muzzle validation");
          }
        });

    int validatedModulesCount = allMismatches.size();
    if (validatedModulesCount == 0) {
      String errorMessage = "Did not found any InstrumentationModule to validate!";
      System.err.println(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  /**
   * Prints all references from all instrumentation modules present in the passed {@code
   * instrumentationClassLoader}.
   *
   * <p>Called by the {@code printMuzzleReferences} gradle task.
   */
  public static void printMuzzleReferences(ClassLoader instrumentationClassLoader)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Class<?> matcherClass =
        instrumentationClassLoader.loadClass(
            "io.opentelemetry.javaagent.tooling.muzzle.ReferencesPrinter");
    matcherClass.getMethod("printMuzzleReferences").invoke(null);
  }

  private MuzzleGradlePluginUtil() {}
}
