/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

public class InnerClassLambdaMetafactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.invoke.InnerClassLambdaMetafactory");
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
                    return new MetaFactoryClassVisitor(
                        classVisitor, instrumentedType.getInternalName());
                  }
                }));
  }

  private static class MetaFactoryClassVisitor extends ClassVisitor {
    private final String slashClassName;

    MetaFactoryClassVisitor(ClassVisitor cv, String slashClassName) {
      super(Opcodes.ASM7, cv);
      this.slashClassName = slashClassName;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      // depending on jdk version we instrument either spinInnerClass or generateInnerClass
      if (("spinInnerClass".equals(name) || "generateInnerClass".equals(name))
          && "()Ljava/lang/Class;".equals(descriptor)) {
        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                // look for a call to ASM ClassWriter.toByteArray() and insert transformation
                // after it
                if (opcode == Opcodes.INVOKEVIRTUAL
                    && "toByteArray".equals(name)
                    && "()[B".equals(descriptor)) {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitFieldInsn(
                      Opcodes.GETFIELD, slashClassName, "lambdaClassName", "Ljava/lang/String;");
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  // targetClass is used to get the ClassLoader where lambda class will be defined
                  mv.visitFieldInsn(
                      Opcodes.GETFIELD, slashClassName, "targetClass", "Ljava/lang/Class;");
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(LambdaTransformer.class),
                      "transform",
                      "([BLjava/lang/String;Ljava/lang/Class;)[B",
                      false);
                }
              }
            };
      }
      return mv;
    }
  }
}
