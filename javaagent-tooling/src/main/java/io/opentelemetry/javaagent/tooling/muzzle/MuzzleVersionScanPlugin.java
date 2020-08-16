/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import net.bytebuddy.dynamic.ClassFileLocator;

/**
 * Entry point for muzzle version scan gradle plugin.
 *
 * <p>For each instrumenter on the classpath, run muzzle validation and throw an exception if any
 * mismatches are detected.
 *
 * <p>Additionally, after a successful muzzle validation run each instrumenter's helper injector.
 */
public class MuzzleVersionScanPlugin {

  public static void assertInstrumentationMuzzled(
      ClassLoader instrumentationLoader, ClassLoader userClassLoader, boolean assertPass)
      throws Exception {
    // muzzle validate all instrumenters
    for (Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, instrumentationLoader)) {
      if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
        // TraceConfigInstrumentation doesn't do muzzle checks
        // check on TracerClassInstrumentation instead
        instrumenter =
            (Instrumenter)
                instrumentationLoader
                    .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                    .getDeclaredConstructor()
                    .newInstance();
      }
      if (!(instrumenter instanceof Instrumenter.Default)) {
        // only default Instrumenters use muzzle. Skip custom instrumenters.
        continue;
      }
      Method m = null;
      try {
        m = instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle");
        m.setAccessible(true);
        ReferenceMatcher muzzle = (ReferenceMatcher) m.invoke(instrumenter);
        List<Reference.Mismatch> mismatches = muzzle.getMismatchedReferenceSources(userClassLoader);

        boolean classLoaderMatch =
            ((Instrumenter.Default) instrumenter).classLoaderMatcher().matches(userClassLoader);
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

          for (Reference.Mismatch mismatch : mismatches) {
            System.err.println("-- " + mismatch);
          }
          throw new RuntimeException("Instrumentation failed Muzzle validation");
        }
      } finally {
        if (null != m) {
          m.setAccessible(false);
        }
      }
    }
    // run helper injector on all instrumenters
    if (assertPass) {
      for (Instrumenter instrumenter :
          ServiceLoader.load(Instrumenter.class, instrumentationLoader)) {
        if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
          // TraceConfigInstrumentation doesn't do muzzle checks
          // check on TracerClassInstrumentation instead
          instrumenter =
              (Instrumenter)
                  instrumentationLoader
                      .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                      .getDeclaredConstructor()
                      .newInstance();
        }
        if (!(instrumenter instanceof Instrumenter.Default)) {
          // only default Instrumenters use muzzle. Skip custom instrumenters.
          continue;
        }
        Instrumenter.Default defaultInstrumenter = (Instrumenter.Default) instrumenter;
        try {
          // verify helper injector works
          String[] helperClassNames = defaultInstrumenter.helperClassNames();
          if (helperClassNames.length > 0) {
            new HelperInjector(
                    MuzzleVersionScanPlugin.class.getSimpleName(),
                    createHelperMap(defaultInstrumenter))
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

  private static Map<String, byte[]> createHelperMap(Instrumenter.Default instrumenter)
      throws IOException {
    Map<String, byte[]> helperMap = new LinkedHashMap<>(instrumenter.helperClassNames().length);
    for (String helperName : instrumenter.helperClassNames()) {
      ClassFileLocator locator =
          ClassFileLocator.ForClassLoader.of(instrumenter.getClass().getClassLoader());
      byte[] classBytes = locator.locate(helperName).resolve();
      helperMap.put(helperName, classBytes);
    }
    return helperMap;
  }

  public static void printMuzzleReferences(ClassLoader instrumentationLoader) {
    for (Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, instrumentationLoader)) {
      if (instrumenter instanceof Instrumenter.Default) {
        try {
          Method getMuzzleMethod =
              instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle");
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

  private MuzzleVersionScanPlugin() {}
}
