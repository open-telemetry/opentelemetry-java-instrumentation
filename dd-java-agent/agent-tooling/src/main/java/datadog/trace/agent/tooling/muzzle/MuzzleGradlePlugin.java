package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;

public class MuzzleGradlePlugin implements Plugin {
  // TODO:
  // - Optimizations
  //   - Cache safe and unsafe classloaders
  //   - Do reference generation at compile time
  //   - lazy-load reference muzzle field
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

  private static final TypeDescription InstrumenterTypeDesc =
      new TypeDescription.ForLoadedType(Instrumenter.class);

  @Override
  public boolean matches(final TypeDescription target) {
    // AutoService annotation is not retained at runtime. Check for instrumenter supertype
    boolean isInstrumenter = false;
    TypeDefinition instrumenter = target;
    while (instrumenter != null) {
      if (instrumenter.getInterfaces().contains(InstrumenterTypeDesc)) {
        isInstrumenter = true;
        break;
      }
      instrumenter = instrumenter.getSuperClass();
    }
    return isInstrumenter;
  }

  @Override
  public Builder<?> apply(Builder<?> builder, TypeDescription typeDescription) {
    return builder.visit(new MuzzleVisitor());
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
