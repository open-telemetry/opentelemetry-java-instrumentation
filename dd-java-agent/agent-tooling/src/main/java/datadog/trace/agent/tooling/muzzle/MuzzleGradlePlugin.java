package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
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
}
