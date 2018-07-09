package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;

public class MuzzleGradlePlugin implements Plugin {
  // TODO:
  // - Additional references to check
  //   - Fields
  //   - methods
  //     - visit annotations
  //     - visit parameter types
  //     - visit method instructions
  //     - method invoke type
  //   - access flags (including implicit package-private)
  //   - supertypes
  // - Misc
  //   - Also match interfaces which extend Instrumenter
  //   - Expose config instead of hardcoding datadog namespace (or reconfigure classpath)
  //   - Run muzzle in matching phase (may require a rewrite of the instrumentation api)
  //   - Documentation
  //   - Fix TraceConfigInstrumentation
  //   - assert no muzzle field defined in instrumentation
  //   - make getMuzzle final in default and remove in gradle plugin
  //   - pull muzzle field + method names into static constants

  private static final TypeDescription DefaultInstrumenterTypeDesc =
      new TypeDescription.ForLoadedType(Instrumenter.Default.class);

  @Override
  public boolean matches(final TypeDescription target) {
    // AutoService annotation is not retained at runtime. Check for Instrumenter.Default supertype
    boolean isInstrumenter = false;
    TypeDefinition instrumenter = target;
    while (instrumenter != null) {
      if (instrumenter.equals(DefaultInstrumenterTypeDesc)) {
        isInstrumenter = true;
        break;
      }
      instrumenter = instrumenter.getSuperClass();
    }
    return isInstrumenter;
  }

  @Override
  public Builder<?> apply(Builder<?> builder, TypeDescription typeDescription) {
    if (typeDescription.equals(DefaultInstrumenterTypeDesc)) {
      // FIXME
      System.out.println("~~~~FIXME: remove final modifier on Default: " + typeDescription);
      return builder;
    } else {
      return builder.visit(new MuzzleVisitor());
    }
  }

  public static class NoOp implements Plugin {
    @Override
    public boolean matches(final TypeDescription target) {
      return false;
    }

    @Override
    public Builder<?> apply(Builder<?> builder, TypeDescription typeDescription) {
      return builder;
    }
  }

  public static void assertAllInstrumentationIsMuzzled(ClassLoader cl) throws Exception {
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
}
