package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Entry point for muzzle version scan gradle plugin.
 *
 * <p>For each instrumenter on the classpath, run muzzle validation and throw an exception if any
 * mismatches are detected.
 *
 * <p>Additionally, after a successful muzzle validation run each instrumenter's helper injector.
 */
public class MuzzleVersionScanPlugin {
  public static void assertInstrumentationNotMuzzled(ClassLoader cl) throws Exception {
    // muzzle validate all instrumenters
    for (Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, MuzzleGradlePlugin.class.getClassLoader())) {
      if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
        // TraceConfigInstrumentation doesn't do muzzle checks
        // check on TracerClassInstrumentation instead
        instrumenter =
            (Instrumenter)
                MuzzleGradlePlugin.class
                    .getClassLoader()
                    .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                    .getDeclaredConstructor()
                    .newInstance();
      }
      Method m = null;
      try {
        m = instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle");
        m.setAccessible(true);
        ReferenceMatcher muzzle = (ReferenceMatcher) m.invoke(instrumenter);
        List<Reference.Mismatch> mismatches = muzzle.getMismatchedReferenceSources(cl);
        if (mismatches.size() > 0) {
          System.err.println(
              "FAILED MUZZLE VALIDATION: " + instrumenter.getClass().getName() + " mismatches:");
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
    for (Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, MuzzleGradlePlugin.class.getClassLoader())) {
      if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
        // TraceConfigInstrumentation doesn't do muzzle checks
        // check on TracerClassInstrumentation instead
        instrumenter =
            (Instrumenter)
                MuzzleGradlePlugin.class
                    .getClassLoader()
                    .loadClass(instrumenter.getClass().getName() + "$TracerClassInstrumentation")
                    .getDeclaredConstructor()
                    .newInstance();
      }
      try {
        // Ratpack injects the scope manager as a helper.
        // This is likely a bug, but we'll grandfather it out of the helper checks for now.
        if (!instrumenter.getClass().getName().contains("Ratpack")) {
          // verify helper injector works
          final String[] helperClassNames = instrumenter.helperClassNames();
          if (helperClassNames.length > 0) {
            new HelperInjector(helperClassNames).transform(null, null, cl, null);
          }
        }
      } catch (Exception e) {
        System.err.println(
            "FAILED HELPER INJECTION. Are Helpers being injected in the correct order?");
        throw e;
      }
    }
  }

  private MuzzleVersionScanPlugin() {}
}
