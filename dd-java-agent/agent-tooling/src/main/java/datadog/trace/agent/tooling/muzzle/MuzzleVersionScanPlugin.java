package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.WeakMap;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
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
  static {
    // prevent WeakMap from logging warning while plugin is running
    WeakMap.Provider.registerIfAbsent(
        new WeakMap.Supplier() {
          @Override
          public <K, V> WeakMap<K, V> get() {
            return new WeakMap.MapAdapter<>(Collections.synchronizedMap(new WeakHashMap<K, V>()));
          }
        });
  }

  public static void assertInstrumentationMuzzled(
      final ClassLoader instrumentationLoader,
      final ClassLoader userClassLoader,
      final boolean assertPass)
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
        final ReferenceMatcher muzzle = (ReferenceMatcher) m.invoke(instrumenter);
        final List<Reference.Mismatch> mismatches =
            muzzle.getMismatchedReferenceSources(userClassLoader);
        final boolean passed = mismatches.size() == 0;
        if (mismatches.size() > 0) {}
        if (passed && !assertPass) {
          System.err.println(
              "MUZZLE PASSED "
                  + instrumenter.getClass().getSimpleName()
                  + " BUT FAILURE WAS EXPECTED");
          throw new RuntimeException("Instrumentation unexpectedly passed Muzzle validation");
        } else if (!passed && assertPass) {
          System.err.println(
              "FAILED MUZZLE VALIDATION: " + instrumenter.getClass().getName() + " mismatches:");
          for (final Reference.Mismatch mismatch : mismatches) {
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
          final String[] helperClassNames = defaultInstrumenter.helperClassNames();
          if (helperClassNames.length > 0) {
            new HelperInjector(createHelperMap(defaultInstrumenter))
                .transform(null, null, userClassLoader, null);
          }
        } catch (final Exception e) {
          System.err.println(
              "FAILED HELPER INJECTION. Are Helpers being injected in the correct order?");
          throw e;
        }
      }
    }
  }

  private static Map<String, byte[]> createHelperMap(Instrumenter.Default instrumenter)
      throws IOException {
    final Map<String, byte[]> helperMap =
        new LinkedHashMap<>(instrumenter.helperClassNames().length);
    for (final String helperName : instrumenter.helperClassNames()) {
      final ClassFileLocator locator =
          ClassFileLocator.ForClassLoader.of(instrumenter.getClass().getClassLoader());
      final byte[] classBytes = locator.locate(helperName).resolve();
      helperMap.put(helperName, classBytes);
    }
    return helperMap;
  }

  public static void printMuzzleReferences(final ClassLoader instrumentationLoader) {
    for (final Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, instrumentationLoader)) {
      if (instrumenter instanceof Instrumenter.Default) {
        try {
          final Method getMuzzleMethod =
              instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle");
          final ReferenceMatcher muzzle;
          try {
            getMuzzleMethod.setAccessible(true);
            muzzle = (ReferenceMatcher) getMuzzleMethod.invoke(instrumenter);
          } finally {
            getMuzzleMethod.setAccessible(false);
          }
          System.out.println(instrumenter.getClass().getName());
          for (final Reference ref : muzzle.getReferences()) {
            System.out.println(prettyPrint("  ", ref));
          }
        } catch (final Exception e) {
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

  private static String prettyPrint(final String prefix, final Reference ref) {
    final StringBuilder builder = new StringBuilder(prefix).append(ref.getClassName());
    if (ref.getSuperName() != null) {
      builder.append(" extends<").append(ref.getSuperName()).append(">");
    }
    if (ref.getInterfaces().size() > 0) {
      builder.append(" implements ");
      for (final String iface : ref.getInterfaces()) {
        builder.append(" <").append(iface).append(">");
      }
    }
    for (final Reference.Source source : ref.getSources()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Source: ").append(source.toString());
    }
    for (final Reference.Field field : ref.getFields()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Field: ");
      for (final Reference.Flag flag : field.getFlags()) {
        builder.append(flag).append(" ");
      }
      builder.append(field.toString());
    }
    for (final Reference.Method method : ref.getMethods()) {
      builder.append("\n").append(prefix).append(prefix);
      builder.append("Method: ");
      for (final Reference.Flag flag : method.getFlags()) {
        builder.append(flag).append(" ");
      }
      builder.append(method.toString());
    }
    return builder.toString();
  }

  private MuzzleVersionScanPlugin() {}
}
