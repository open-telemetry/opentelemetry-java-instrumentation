/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import net.bytebuddy.dynamic.ClassFileLocator;

/** Entry point for the muzzle gradle plugin. */
public final class MuzzleGradlePluginUtil {

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
   *   <li>any helper class defined in {@link InstrumentationModule#getMuzzleHelperClassNames()} or
   *       {@link InstrumentationModule#additionalHelperClassNames()} fails to be injected into
   *       {@code userClassLoader}
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
    for (InstrumentationModule instrumentationModule :
        ServiceLoader.load(InstrumentationModule.class, agentClassLoader)) {
      Method getMuzzleReferenceMatcher = null;
      try {
        getMuzzleReferenceMatcher =
            InstrumentationModule.class.getDeclaredMethod("getMuzzleReferenceMatcher");
        getMuzzleReferenceMatcher.setAccessible(true);
        ReferenceMatcher muzzle =
            (ReferenceMatcher) getMuzzleReferenceMatcher.invoke(instrumentationModule);
        List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(userClassLoader);

        boolean classLoaderMatch =
            instrumentationModule.classLoaderMatcher().matches(userClassLoader);
        boolean passed = mismatches.isEmpty() && classLoaderMatch;

        if (passed && !assertPass) {
          System.err.println(
              "MUZZLE PASSED "
                  + instrumentationModule.getClass().getSimpleName()
                  + " BUT FAILURE WAS EXPECTED");
          throw new RuntimeException("Instrumentation unexpectedly passed Muzzle validation");
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
          throw new RuntimeException("Instrumentation failed Muzzle validation");
        }
      } finally {
        if (null != getMuzzleReferenceMatcher) {
          getMuzzleReferenceMatcher.setAccessible(false);
        }
      }
    }
    // run helper injector on all instrumenters
    if (assertPass) {
      for (InstrumentationModule instrumentationModule :
          ServiceLoader.load(InstrumentationModule.class, agentClassLoader)) {
        try {
          // verify helper injector works
          List<String> allHelperClasses = instrumentationModule.getAllHelperClassNames();
          if (!allHelperClasses.isEmpty()) {
            new HelperInjector(
                    MuzzleGradlePluginUtil.class.getSimpleName(),
                    createHelperMap(allHelperClasses, agentClassLoader))
                .transform(null, null, userClassLoader, null);
          }
        } catch (Exception e) {
          System.err.println(
              "FAILED HELPER INJECTION. Are Helpers being injected in the correct order?");
          throw e;
        }
      }
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
        Method getMuzzleMethod =
            instrumentationModule.getClass().getDeclaredMethod("getMuzzleReferenceMatcher");
        ReferenceMatcher muzzle;
        try {
          getMuzzleMethod.setAccessible(true);
          muzzle = (ReferenceMatcher) getMuzzleMethod.invoke(instrumentationModule);
        } finally {
          getMuzzleMethod.setAccessible(false);
        }
        System.out.println(instrumentationModule.getClass().getName());
        for (Reference ref : muzzle.getReferences()) {
          System.out.println(prettyPrint("  ", ref));
        }
      } catch (Exception e) {
        String message =
            "Unexpected exception printing references for "
                + instrumentationModule.getClass().getName();
        System.out.println(message);
        throw new RuntimeException(message, e);
      }
    }
  }

  private static String prettyPrint(String prefix, Reference ref) {
    StringBuilder builder = new StringBuilder(prefix).append(ref.getClassName());
    if (ref.getSuperName() != null) {
      builder.append(" extends<").append(ref.getSuperName()).append(">");
    }
    if (!ref.getInterfaces().isEmpty()) {
      builder.append(" implements ");
      for (String iface : ref.getInterfaces()) {
        builder.append(" <").append(iface).append(">");
      }
    }
    for (Reference.Source source : ref.getSources()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Source: ").append(source.toString());
    }
    for (Reference.Field field : ref.getFields()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Field: ");
      for (Reference.Flag flag : field.getFlags()) {
        builder.append(flag).append(" ");
      }
      builder.append(field.toString());
    }
    for (Reference.Method method : ref.getMethods()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Method: ");
      for (Reference.Flag flag : method.getFlags()) {
        builder.append(flag).append(" ");
      }
      builder.append(method.toString());
    }
    return builder.toString();
  }

  private MuzzleGradlePluginUtil() {}
}
