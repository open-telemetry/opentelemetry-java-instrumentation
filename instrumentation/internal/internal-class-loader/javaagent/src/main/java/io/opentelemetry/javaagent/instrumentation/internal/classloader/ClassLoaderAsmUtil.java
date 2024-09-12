/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.bootstrap.DefineClassHelper;
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class ClassLoaderAsmUtil {

  private ClassLoaderAsmUtil() {}

  public static AgentBuilder.Transformer getBootDelegationTransformer() {
    return getTransformer(
        classVisitor ->
            new LoadClassVisitor(
                classVisitor,
                Type.getInternalName(BootDelegationHelper.class),
                "onEnter",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false));
  }

  public static AgentBuilder.Transformer getInjectedClassTransformer() {
    return getTransformer(
        classVisitor ->
            new LoadClassVisitor(
                classVisitor,
                Type.getInternalName(InjectedClassHelper.class),
                "loadHelperClass",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;",
                true));
  }

  public static AgentBuilder.Transformer getDefineClassTransformer() {
    return getTransformer(classVisitor -> new DefineClassVisitor(classVisitor));
  }

  private static AgentBuilder.Transformer getTransformer(
      Function<ClassVisitor, ClassVisitor> wrapper) {
    return (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
        builder.visit(
            new AsmVisitorWrapper() {

              @Override
              public int mergeWriter(int flags) {
                return flags | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
              }

              @Override
              @CanIgnoreReturnValue
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
                return wrapper.apply(classVisitor);
              }
            });
  }

  private static class LoadClassVisitor extends ClassVisitor {
    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final boolean loadThis;

    protected LoadClassVisitor(
        ClassVisitor classVisitor,
        String className,
        String methodName,
        String methodDescriptor,
        boolean loadThis) {
      super(AsmApi.VERSION, classVisitor);
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
      this.loadThis = loadThis;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

      boolean v1 = "(Ljava/lang/String)Ljava/lang/Class;".equals(signature);
      boolean v2 = "(Ljava/lang/String;Z)Ljava/lang/Class;".equals(signature);
      if ("loadClass".equals(name)
          && (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) > 0
          && (access & Opcodes.ACC_STATIC) == 0
          && (v1 || v2)) {

        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitCode() {
                if (loadThis) {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                }
                // load class name argument
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                // invoke helper
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC, className, methodName, methodDescriptor, false);
                // duplicate helper return value on stack
                mv.visitInsn(Opcodes.DUP);
                // go to injected code end when returned value is null
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFNULL, endLabel);
                // if not null, return with value from helper
                mv.visitInsn(Opcodes.ARETURN);
                // injected code end
                mv.visitLabel(endLabel);

                // insert original method code
                super.visitCode();
              }
            };
      }
      return mv;
    }
  }

  private static class DefineClassVisitor extends ClassVisitor {

    protected DefineClassVisitor(ClassVisitor classVisitor) {
      super(AsmApi.VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {

      String d1 = "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;";
      String d2 =
          "(Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;)Ljava/lang/Class;";
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

      boolean v1 = d1.equals(descriptor);
      boolean v2 = d2.equals(descriptor);
      if ("defineClass".equals(name) && (v1 || v2)) {
        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitCode() {
                Label beforeBody = new Label();
                Label afterBody = new Label();
                Label handler = new Label();

                mv.visitTryCatchBlock(beforeBody, afterBody, handler, null);

                // enter advice
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                if (v1) {
                  mv.visitVarInsn(Opcodes.ILOAD, 3);
                  mv.visitVarInsn(Opcodes.ILOAD, 4);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(DefineClassHelper.class),
                      "beforeDefineClass",
                      "(Ljava/lang/ClassLoader;Ljava/lang/String;[BII)"
                          + "Lio/opentelemetry/javaagent/bootstrap/DefineClassHelper$Handler$DefineClassContext;",
                      false);
                } else {
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(DefineClassHelper.class),
                      "beforeDefineClass",
                      "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/nio/ByteBuffer;)"
                          + "Lio/opentelemetry/javaagent/bootstrap/DefineClassHelper$Handler$DefineClassContext;",
                      false);
                }
                // helper return value on top of stack

                // start of try block
                mv.visitLabel(beforeBody);

                // original method body
                super.visitCode();

                // end of try block
                mv.visitLabel(afterBody);

                // finally block (exit advice)
                mv.visitLabel(handler);
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(DefineClassHelper.class),
                    "afterDefineClass",
                    "(Lio/opentelemetry/javaagent/bootstrap/DefineClassHelper$Handler$DefineClassContext;)V",
                    false);
              }
            };
      }
      return mv;
    }
  }
}
