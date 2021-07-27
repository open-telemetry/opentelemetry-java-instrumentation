/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.matcher;

import static java.lang.System.lineSeparator;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.muzzle.ClassRef;
import io.opentelemetry.javaagent.extension.muzzle.FieldRef;
import io.opentelemetry.javaagent.extension.muzzle.MethodRef;
import io.opentelemetry.javaagent.extension.muzzle.Source;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.muzzle.Mismatch;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import net.bytebuddy.dynamic.ClassFileLocator;

/** Entry point for the muzzle gradle plugin. */
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
      throws Exception {
    // muzzle validate all instrumenters
    int validatedModulesCount = 0;
    for (InstrumentationModule instrumentationModule :
        ServiceLoader.load(InstrumentationModule.class, agentClassLoader)) {
      ReferenceMatcher muzzle =
          new ReferenceMatcher(
              instrumentationModule.getMuzzleHelperClassNames(),
              instrumentationModule.getMuzzleReferences(),
              instrumentationModule::isHelperClass);
      List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(userClassLoader);

      boolean classLoaderMatch =
          instrumentationModule.classLoaderMatcher().matches(userClassLoader);
      boolean passed = mismatches.isEmpty() && classLoaderMatch;

      if (passed && !assertPass) {
        System.err.println(
            "MUZZLE PASSED "
                + instrumentationModule.getClass().getSimpleName()
                + " BUT FAILURE WAS EXPECTED");
        throw new IllegalStateException("Instrumentation unexpectedly passed Muzzle validation");
      } else if (!passed && assertPass) {
        System.err.println(
            "FAILED MUZZLE VALIDATION: "
                + instrumentationModule.getClass().getName()
                + " mismatches:");

        if (!classLoaderMatch) {
          System.err.println("-- classloader mismatch");
        }

        for (Mismatch mismatch : mismatches) {
          System.err.println("-- " + mismatch);
        }
        throw new IllegalStateException("Instrumentation failed Muzzle validation");
      }

      validatedModulesCount++;
    }
    // run helper injector on all instrumentation modules
    if (assertPass) {
      for (InstrumentationModule instrumentationModule :
          ServiceLoader.load(InstrumentationModule.class, agentClassLoader)) {
        try {
          // verify helper injector works
          List<String> allHelperClasses = instrumentationModule.getMuzzleHelperClassNames();
          if (!allHelperClasses.isEmpty()) {
            new HelperInjector(
                    MuzzleGradlePluginUtil.class.getSimpleName(),
                    createHelperMap(allHelperClasses, agentClassLoader))
                .transform(null, null, userClassLoader, null);
          }
        } catch (RuntimeException e) {
          System.err.println(
              "FAILED HELPER INJECTION. Are Helpers being injected in the correct order?");
          throw e;
        }
      }
    }
    if (validatedModulesCount == 0) {
      String errorMessage = "Did not found any InstrumentationModule to validate!";
      System.err.println(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  private static Map<String, byte[]> createHelperMap(
      Collection<String> helperClassNames, ClassLoader agentClassLoader) throws IOException {
    Map<String, byte[]> helperMap = new LinkedHashMap<>(helperClassNames.size());
    for (String helperName : helperClassNames) {
      ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(agentClassLoader);
      byte[] classBytes = locator.locate(helperName).resolve();
      helperMap.put(helperName, classBytes);
    }
    return helperMap;
  }

  /**
   * Prints all references from all instrumentation modules present in the passed {@code
   * instrumentationClassLoader}.
   *
   * <p>Called by the {@code printMuzzleReferences} gradle task.
   */
  public static void printMuzzleReferences(ClassLoader instrumentationClassLoader) {
    for (InstrumentationModule instrumentationModule :
        ServiceLoader.load(InstrumentationModule.class, instrumentationClassLoader)) {
      try {
        System.out.println(instrumentationModule.getClass().getName());
        for (ClassRef ref : instrumentationModule.getMuzzleReferences().values()) {
          System.out.print(prettyPrint(ref));
        }
      } catch (RuntimeException e) {
        String message =
            "Unexpected exception printing references for "
                + instrumentationModule.getClass().getName();
        System.out.println(message);
        throw new IllegalStateException(message, e);
      }
    }
  }

  private static String prettyPrint(ClassRef ref) {
    StringBuilder builder = new StringBuilder(INDENT).append(ref).append(lineSeparator());
    if (!ref.getSources().isEmpty()) {
      builder.append(INDENT).append(INDENT).append("Sources:").append(lineSeparator());
      for (Source source : ref.getSources()) {
        builder
            .append(INDENT)
            .append(INDENT)
            .append(INDENT)
            .append("at: ")
            .append(source)
            .append(lineSeparator());
      }
    }
    for (FieldRef field : ref.getFields()) {
      builder.append(INDENT).append(INDENT).append(field).append(lineSeparator());
    }
    for (MethodRef method : ref.getMethods()) {
      builder.append(INDENT).append(INDENT).append(method).append(lineSeparator());
    }
    return builder.toString();
  }

  private MuzzleGradlePluginUtil() {}
}
