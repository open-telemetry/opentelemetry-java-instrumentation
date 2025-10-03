/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper.HelperClassInfo;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
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

/**
 * This instrumentation inserts loading of our injected helper classes at the start of {@code
 * ClassLoader.loadClass} method.
 */
public class LoadInjectedClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("java.lang.ClassLoader"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module, protectionDomain) ->
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

  private static class ClassLoaderClassVisitor extends ClassVisitor implements Opcodes {
    private String internalClassName;

    ClassLoaderClassVisitor(ClassVisitor classVisitor) {
      super(AsmApi.VERSION, classVisitor);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      internalClassName = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("loadClass".equals(name)
          && ("(Ljava/lang/String;)Ljava/lang/Class;".equals(descriptor)
              || "(Ljava/lang/String;Z)Ljava/lang/Class;".equals(descriptor))) {

        int argumentCount = Type.getArgumentTypes(descriptor).length;
        return new MethodVisitor(api, mv) {
          @Override
          public void visitCode() {
            super.visitCode();

            // inserts the following at the start of the loadClass method:
            /*
             InjectedClassHelper.HelperClassInfo helperClassInfo = InjectedClassHelper.getHelperClassInfo(this, name);
             if (helperClassInfo != null) {
                 Class<?> clazz = findLoadedClass(name);
                 if (clazz != null) {
                     return clazz;
                 }
                 try {
                     byte[] bytes = helperClassInfo.getClassBytes();
                     return defineClass(name, bytes, 0, bytes.length, helperClassInfo.getProtectionDomain());
                 } catch (LinkageError error) {
                     clazz = findLoadedClass(name);
                     if (clazz != null) {
                         return clazz;
                     }
                     throw error;
                 }
             }
            */

            Label startTry = new Label();
            Label endTry = new Label();
            Label handler = new Label();
            mv.visitTryCatchBlock(startTry, endTry, handler, "java/lang/LinkageError");
            // InjectedClassHelper.HelperClassInfo helperClassInfo =
            // InjectedClassHelper.getHelperClassInfo(this, name);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(
                INVOKESTATIC,
                Type.getInternalName(InjectedClassHelper.class),
                "getHelperClassInfo",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;)"
                    + Type.getDescriptor(HelperClassInfo.class),
                false);
            mv.visitVarInsn(ASTORE, argumentCount + 1); // store helperClassInfo
            mv.visitVarInsn(ALOAD, argumentCount + 1);
            Label notHelperClass = new Label();
            mv.visitJumpInsn(IFNULL, notHelperClass);

            // getHelperClassInfo returned non-null
            // Class<?> clazz = findLoadedClass(name);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                internalClassName,
                "findLoadedClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false);
            mv.visitVarInsn(ASTORE, argumentCount + 2); // store clazz
            mv.visitVarInsn(ALOAD, argumentCount + 2);
            mv.visitJumpInsn(IFNULL, startTry);

            // findLoadedClass returned non-null
            // return clazz
            mv.visitVarInsn(ALOAD, argumentCount + 2);
            mv.visitInsn(ARETURN);

            mv.visitLabel(startTry);
            mv.visitFrame(
                Opcodes.F_APPEND,
                2,
                new Object[] {Type.getInternalName(HelperClassInfo.class), "java/lang/Class"},
                0,
                null);
            // byte[] bytes = helperClassInfo.getClassBytes();
            mv.visitVarInsn(ALOAD, argumentCount + 1);
            mv.visitMethodInsn(
                INVOKEINTERFACE,
                Type.getInternalName(HelperClassInfo.class),
                "getClassBytes",
                "()[B",
                true);
            mv.visitVarInsn(ASTORE, argumentCount + 3); // store bytes

            // return defineClass(name, bytes, 0, bytes.length,
            // helperClassInfo.getProtectionDomain());
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, argumentCount + 3);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ALOAD, argumentCount + 3);
            mv.visitInsn(ARRAYLENGTH);
            mv.visitVarInsn(ALOAD, argumentCount + 1);
            mv.visitMethodInsn(
                INVOKEINTERFACE,
                Type.getInternalName(HelperClassInfo.class),
                "getProtectionDomain",
                "()Ljava/security/ProtectionDomain;",
                true);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                internalClassName,
                "defineClass",
                "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;",
                false);
            mv.visitLabel(endTry);
            mv.visitInsn(ARETURN);

            mv.visitLabel(handler);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/LinkageError"});
            mv.visitVarInsn(ASTORE, argumentCount + 3); // store LinkageError
            // clazz = findLoadedClass(name);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                internalClassName,
                "findLoadedClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false);
            mv.visitVarInsn(ASTORE, argumentCount + 2); // score clazz
            mv.visitVarInsn(ALOAD, argumentCount + 2);
            Label throwError = new Label();
            mv.visitJumpInsn(IFNULL, throwError);
            // return clazz
            mv.visitVarInsn(ALOAD, argumentCount + 2);
            mv.visitInsn(ARETURN);
            mv.visitLabel(throwError);
            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/LinkageError"}, 0, null);
            // throw error
            mv.visitVarInsn(ALOAD, argumentCount + 3);
            mv.visitInsn(ATHROW);

            mv.visitLabel(notHelperClass);
            mv.visitFrame(Opcodes.F_CHOP, 3, null, 0, null);
          }

          @Override
          public void visitMaxs(int maxStack, int maxLocals) {
            // minimally we have argumentCount parameters + this + 3 locals added by us
            super.visitMaxs(maxStack, Math.max(maxLocals, argumentCount + 1 + 3));
          }
        };
      }
      return mv;
    }
  }
}
