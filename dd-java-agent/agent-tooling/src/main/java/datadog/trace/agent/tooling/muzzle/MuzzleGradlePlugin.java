package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

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

  private static class RemoveFinalFlagVisitor implements AsmVisitorWrapper {
    final String methodName;

    public RemoveFinalFlagVisitor(String methodName) {
      this.methodName = methodName;
    }

    @Override
    public int mergeWriter(int flags) {
      return flags;
    }

    @Override
    public int mergeReader(int flags) {
      return flags;
    }

    @Override
    public ClassVisitor wrap(
        TypeDescription instrumentedType,
        ClassVisitor classVisitor,
        Implementation.Context implementationContext,
        TypePool typePool,
        FieldList<FieldDescription.InDefinedShape> fields,
        MethodList<?> methods,
        int writerFlags,
        int readerFlags) {
      return new Visitor(classVisitor);
    }

    private class Visitor extends ClassVisitor {
      public Visitor(ClassVisitor cv) {
        super(Opcodes.ASM6, cv);
      }

      @Override
      public MethodVisitor visitMethod(
          final int access,
          final String name,
          final String descriptor,
          final String signature,
          final String[] exceptions) {
        MethodVisitor methodVisitor =
            super.visitMethod(access, name, descriptor, signature, exceptions);
        if (name.equals(methodName) && (access & Opcodes.ACC_FINAL) != 0) {
          return super.visitMethod(
              access ^ Opcodes.ACC_FINAL, name, descriptor, signature, exceptions);
        } else {
          return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
      }
    }
  }
}
