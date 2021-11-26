/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import static net.bytebuddy.matcher.ElementMatchers.named;

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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ClassInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.Class");
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
                    return new ClassClassVisitor(classVisitor);
                  }
                }));
  }

  private static class ClassClassVisitor extends ClassVisitor {

    ClassClassVisitor(ClassVisitor cv) {
      super(Opcodes.ASM7, cv);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("getInterfaces".equals(name)
          && ("()[Ljava/lang/Class;".equals(descriptor)
              || "(Z)[Ljava/lang/Class;".equals(descriptor))) {
        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                // filter the result of call to getInterfaces0, which is used on hotspot, and
                // J9VMInternals.getInterfaces which is used on openj9
                if (((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL)
                        && "getInterfaces0".equals(name)
                        && "()[Ljava/lang/Class;".equals(descriptor))
                    || (opcode == Opcodes.INVOKESTATIC
                        && "getInterfaces".equals(name)
                        && "java/lang/J9VMInternals".equals(owner)
                        && "(Ljava/lang/Class;)[Ljava/lang/Class;".equals(descriptor))) {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(ReflectionHelper.class),
                      "filterInterfaces",
                      "([Ljava/lang/Class;Ljava/lang/Class;)[Ljava/lang/Class;",
                      false);
                }
              }
            };
      }
      return mv;
    }
  }
}
