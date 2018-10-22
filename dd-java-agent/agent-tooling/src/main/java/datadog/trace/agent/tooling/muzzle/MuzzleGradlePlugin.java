package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
import java.io.IOException;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

/** Bytebuddy gradle plugin which creates muzzle-references at compile time. */
public class MuzzleGradlePlugin implements Plugin {
  private static final TypeDescription DefaultInstrumenterTypeDesc =
      new TypeDescription.ForLoadedType(Instrumenter.Default.class);

  @Override
  public boolean matches(final TypeDescription target) {
    if (target.isAbstract()) {
      return false;
    }
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
  public DynamicType.Builder<?> apply(
      final DynamicType.Builder<?> builder,
      final TypeDescription typeDescription,
      final ClassFileLocator classFileLocator) {
    return builder.visit(new MuzzleVisitor());
  }

  @Override
  public void close() throws IOException {}

  /** Compile-time Optimization used by gradle buildscripts. */
  public static class NoOp implements Plugin {
    @Override
    public boolean matches(final TypeDescription target) {
      return false;
    }

    @Override
    public DynamicType.Builder<?> apply(
        final DynamicType.Builder<?> builder,
        final TypeDescription typeDescription,
        final ClassFileLocator classFileLocator) {
      return builder;
    }

    @Override
    public void close() throws IOException {}
  }
}
