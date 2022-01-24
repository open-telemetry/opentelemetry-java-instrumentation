/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.DefineClassContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class DefineClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.ClassLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module) ->
            builder.visit(
                new AsmVisitorWrapper() {
                  @Override
                  public int mergeWriter(int flags) {
                    return flags | ClassWriter.COMPUTE_MAXS;
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
                    return new ClassLoaderClassVisitor(classVisitor);
                  }
                }));
  }

  private static class ClassLoaderClassVisitor extends ClassVisitor {

    ClassLoaderClassVisitor(ClassVisitor cv) {
      super(Opcodes.ASM7, cv);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      // apply the following transformation to defineClass method
      /*
      try {
        // original method body
      } catch (LinkageError error) {
        Class<?> loaded = findLoadedClass(className);
        return DefineClassUtil.handleLinkageError(error, loaded);
      }
       */
      if ("defineClass".equals(name)
          && ("(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;"
                  .equals(descriptor)
              || "(Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;)Ljava/lang/Class;"
                  .equals(descriptor))) {
        mv =
            new MethodVisitor(api, mv) {
              Label start = new Label();
              Label end = new Label();

              @Override
              public void visitCode() {
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(DefineClassContext.class),
                    "enter",
                    "()V",
                    false);
                mv.visitTryCatchBlock(start, end, end, "java/lang/LinkageError");
                mv.visitLabel(start);

                super.visitCode();
              }

              @Override
              public void visitInsn(int opcode) {
                if (opcode == Opcodes.ARETURN) {
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(DefineClassContext.class),
                      "exit",
                      "()V",
                      false);
                }
                super.visitInsn(opcode);
              }

              @Override
              public void visitMaxs(int maxStack, int maxLocals) {
                mv.visitLabel(end);
                mv.visitFrame(
                    Opcodes.F_FULL,
                    2,
                    new Object[] {"java/lang/ClassLoader", "java/lang/String"},
                    1,
                    new Object[] {"java/lang/LinkageError"});
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(DefineClassContext.class),
                    "exitAndGet",
                    "()Z",
                    false);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/ClassLoader",
                    "findLoadedClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false);
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(DefineClassUtil.class),
                    "handleLinkageError",
                    "(Ljava/lang/LinkageError;ZLjava/lang/Class;)Ljava/lang/Class;",
                    false);
                mv.visitInsn(Opcodes.ARETURN);

                super.visitMaxs(maxStack, maxLocals);
              }
            };
      }
      return mv;
    }
  }
}
