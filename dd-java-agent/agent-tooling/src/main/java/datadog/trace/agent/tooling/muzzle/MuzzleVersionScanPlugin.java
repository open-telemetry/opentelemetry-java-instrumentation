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

  public static void printMuzzleReferences() {
    for (Instrumenter instrumenter :
        ServiceLoader.load(Instrumenter.class, MuzzleGradlePlugin.class.getClassLoader())) {
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
    final StringBuilder builder = new StringBuilder(prefix).append(ref.getClassName());
    if (ref.getSuperName() != null) {
      builder.append(" extends<").append(ref.getSuperName()).append(">");
    }
    if (ref.getInterfaces().size() > 0) {
      builder.append(" implements ");
      for (String iface : ref.getInterfaces()) {
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
      for (Reference.Flag flag : field.getFlags()) {
        builder.append(flag).append(" ");
      }
      builder.append(field.toString());
    }
    for (final Reference.Method method : ref.getMethods()) {
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
