package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;

/** Bytebuddy gradle plugin which creates muzzle-references at compile time. */
public class MuzzleGradlePlugin implements Plugin {
  private static final TypeDescription DefaultInstrumenterTypeDesc =
      new TypeDescription.ForLoadedType(Instrumenter.Default.class);

  @Override
  public boolean matches(final TypeDescription target) {
    // AutoService annotation is not retained at runtime. Check for Instrumenter.Default supertype
    boolean isInstrumenter = false;
    TypeDefinition instrumenter = null == target ? null : target.getSuperClass();
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
    return builder.visit(new MuzzleVisitor());
  }

  /** Compile-time Optimization used by gradle buildscripts. */
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
