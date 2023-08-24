/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.lang.reflect.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class OldBytecode {

  private OldBytecode() {}

  /**
   * Generates and run a simple class with a {@link #toString()} implementation as if it had been
   * compiled on an older java compiler
   *
   * @param className class name
   * @param version bytecode version
   * @return "toString"
   */
  public static String generateAndRun(String className, ClassFileVersion version) {
    try (DynamicType.Unloaded<Object> unloadedClass = makeClass(className, version)) {
      Class<?> generatedClass = unloadedClass.load(OldBytecode.class.getClassLoader()).getLoaded();

      return generatedClass.getConstructor().newInstance().toString();

    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static DynamicType.Unloaded<Object> makeClass(
      String className, ClassFileVersion version) {
    return new ByteBuddy(version)
        .subclass(Object.class)
        // required otherwise stack frames aren't computed when needed
        .visit(
            version.isAtLeast(ClassFileVersion.JAVA_V7)
                ? new ComputeFramesAsmVisitorWrapper()
                : AsmVisitorWrapper.NoOp.INSTANCE)
        .name(className)
        .defineMethod("toString", String.class, Modifier.PUBLIC)
        .intercept(new ToStringMethod())
        .make();
  }

  private static class ToStringMethod implements Implementation, ByteCodeAppender {

    @Override
    public ByteCodeAppender appender(Target implementationTarget) {
      return this;
    }

    @Override
    public InstrumentedType prepare(InstrumentedType instrumentedType) {
      return instrumentedType;
    }

    @Override
    public Size apply(
        MethodVisitor methodVisitor,
        Context implementationContext,
        MethodDescription instrumentedMethod) {

      // Bytecode archeology:
      //
      // JSR and RET bytecode instructions were used to create "subroutines". Those were used
      // in try/catch blocks as an attempt to avoid some bytecode duplication, this was later
      // replaced with inlining.
      // Starting from Java 5, no java compiler is expected to issue bytecode containing them and
      // the JVM bytecode validation will reject it.
      //
      // Java 7 bytecode introduced the concept of "stack map frames", which describe the types of
      // the objects that are stored on the stack during method body execution.
      //
      // As a consequence, the code below allows to test the following combinations:
      // - java 1 to java 4 bytecode with JSR/RET opcodes
      // - java 5 and java 6 bytecode without stack map frames
      // - java 7 and later bytecode with stack map frames, those are automatically added by the
      //   ComputeFramesAsmVisitorWrapper.
      //
      boolean useJsrRet =
          implementationContext.getClassFileVersion().isLessThan(ClassFileVersion.JAVA_V5);

      if (useJsrRet) {
        // return "toString";
        //
        // using obsolete JSR/RET instructions
        Label target = new Label();
        methodVisitor.visitJumpInsn(Opcodes.JSR, target);

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitLabel(target);
        methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
        methodVisitor.visitLdcInsn("toString");
        methodVisitor.visitVarInsn(Opcodes.ASTORE, 1);
        methodVisitor.visitVarInsn(Opcodes.RET, 2);
        return new Size(1, 3);
      } else {
        // try {
        //   return "toString";
        // } catch (Throwable e) {
        //   return e.getMessage();
        // }
        //
        // the Throwable exception is added to stack map frames with java7+, and needs to be
        // added when upgrading the bytecode
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();

        methodVisitor.visitTryCatchBlock(
            start, end, handler, Type.getInternalName(Throwable.class));
        methodVisitor.visitLabel(start);
        methodVisitor.visitLdcInsn("toString");
        methodVisitor.visitLabel(end);

        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitLabel(handler);
        methodVisitor.visitVarInsn(Opcodes.ASTORE, 1);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);

        methodVisitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            Type.getInternalName(Throwable.class),
            "getMessage",
            Type.getMethodDescriptor(Type.getType(String.class)),
            false);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        return new Size(1, 2);
      }
    }
  }
}
