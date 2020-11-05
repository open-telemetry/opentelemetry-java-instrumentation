/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.javaagent.tooling.Instrumenter.Default;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;

/** Entry point for the muzzle gradle plugin. */
public final class MuzzleGradlePluginUtil {

  /**
   * Verifies that all instrumenters present in the {@code agentClassLoader} can be safely applied
   * to the passed {@code userClassLoader}.
   *
   * <p>This method throws whenever one of the following step fails (and {@code assertPass} is
   * true):
   *
   * <ol>
   *   <li>{@code userClassLoader} is not matched by the {@link Default#classLoaderMatcher()} method
   *   <li>{@link ReferenceMatcher} of any instrumenter finds any mismatch
   *   <li>any helper class defined in {@link Default#helperClassNames()} fails to be injected into
   *       {@code userClassLoader}
   * </ol>
   *
   * When {@code assertPass = false} this method behaves in an opposite way: failure in any of the
   * first two steps is expected (helper classes are not injected at all).
   *
   * <p>This method is repeatedly called by the {@code :muzzle} gradle task - each tested dependency
   * version passes different {@code userClassLoader}.
   */
  public static void assertInstrumentationMuzzled(
      ClassLoader agentClassLoader, ClassLoader userClassLoader, boolean assertPass)
      throws Exception {
    // muzzle validate all instrumenters
    for (Object instrumenter : loadAllInstrumenters(agentClassLoader)) {
      if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
        // TraceConfigInstrumentation doesn't do muzzle checks
        // check on TracerClassInstrumentation instead
        instrumenter =
            agentClassLoader
                .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                .getDeclaredConstructor()
                .newInstance();
      }
      if (!(instrumenter instanceof Instrumenter.Default
          || instrumenter instanceof InstrumentationModule)) {
        // only default Instrumenters and modules use muzzle. Skip custom instrumenters.
        continue;
      }
      Method getMuzzleReferenceMatcher = null;
      try {
        getMuzzleReferenceMatcher =
            instrumenter.getClass().getDeclaredMethod("getMuzzleReferenceMatcher");
        getMuzzleReferenceMatcher.setAccessible(true);
        ReferenceMatcher muzzle = (ReferenceMatcher) getMuzzleReferenceMatcher.invoke(instrumenter);
        List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(userClassLoader);

        Method getClassLoaderMatcher = instrumenter.getClass().getMethod("classLoaderMatcher");
        getClassLoaderMatcher.setAccessible(true);
        boolean classLoaderMatch =
            ((ElementMatcher<ClassLoader>) getClassLoaderMatcher.invoke(instrumenter))
                .matches(userClassLoader);
        boolean passed = mismatches.isEmpty() && classLoaderMatch;

        if (passed && !assertPass) {
          System.err.println(
              "MUZZLE PASSED "
                  + instrumenter.getClass().getSimpleName()
                  + " BUT FAILURE WAS EXPECTED");
          throw new RuntimeException("Instrumentation unexpectedly passed Muzzle validation");
        } else if (!passed && assertPass) {
          System.err.println(
              "FAILED MUZZLE VALIDATION: " + instrumenter.getClass().getName() + " mismatches:");

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
      for (Object instrumenter : loadAllInstrumenters(agentClassLoader)) {
        if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
          // TraceConfigInstrumentation doesn't do muzzle checks
          // check on TracerClassInstrumentation instead
          instrumenter =
              agentClassLoader
                  .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                  .getDeclaredConstructor()
                  .newInstance();
        }
        if (!(instrumenter instanceof Instrumenter.Default
            || instrumenter instanceof InstrumentationModule)) {
          // only default Instrumenters and modules use muzzle. Skip custom instrumenters.
          continue;
        }
        try {
          // verify helper injector works
          Method getHelperClassNames = instrumenter.getClass().getMethod("helperClassNames");
          getHelperClassNames.setAccessible(true);
          String[] helperClassNames = (String[]) getHelperClassNames.invoke(instrumenter);
          if (helperClassNames.length > 0) {
            new HelperInjector(
                    MuzzleGradlePluginUtil.class.getSimpleName(),
                    createHelperMap(helperClassNames, agentClassLoader))
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
      String[] helperClassNames, ClassLoader agentClassLoader) throws IOException {
    Map<String, byte[]> helperMap = new LinkedHashMap<>(helperClassNames.length);
    for (String helperName : helperClassNames) {
      ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(agentClassLoader);
      byte[] classBytes = locator.locate(helperName).resolve();
      helperMap.put(helperName, classBytes);
    }
    return helperMap;
  }

  /**
   * Prints all references from all instrumenters present in the passed {@code
   * instrumentationClassLoader}.
   *
   * <p>Called by the {@code printMuzzleReferences} gradle task.
   */
  public static void printMuzzleReferences(ClassLoader instrumentationClassLoader) {
    for (Object instrumenter : loadAllInstrumenters(instrumentationClassLoader)) {
      if (instrumenter instanceof Instrumenter.Default
          || instrumenter instanceof InstrumentationModule) {
        try {
          Method getMuzzleMethod =
              instrumenter.getClass().getDeclaredMethod("getMuzzleReferenceMatcher");
          ReferenceMatcher muzzle;
          try {
            getMuzzleMethod.setAccessible(true);
            muzzle = (ReferenceMatcher) getMuzzleMethod.invoke(instrumenter);
          } finally {
            getMuzzleMethod.setAccessible(false);
          }
          System.out.println(instrumenter.getClass().getName());
          for (Reference ref : muzzle.getReferences()) {
            System.out.println(prettyPrint("  ", ref));
          }
        } catch (Exception e) {
          System.out.println(
              "Unexpected exception printing references for " + instrumenter.getClass().getName());
          throw new RuntimeException(e);
        }
      } else {
        throw new RuntimeException(
            "class "
                + instrumenter.getClass().getName()
                + " is not a default instrumenter. No refs to print.");
      }
    }
  }

  private static List<Object> loadAllInstrumenters(ClassLoader instrumentationClassLoader) {
    return Stream.concat(
            StreamSupport.stream(
                ServiceLoader.load(Instrumenter.class, instrumentationClassLoader).spliterator(),
                false),
            StreamSupport.stream(
                ServiceLoader.load(InstrumentationModule.class, instrumentationClassLoader)
                    .spliterator(),
                false))
        .collect(Collectors.toList());
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
