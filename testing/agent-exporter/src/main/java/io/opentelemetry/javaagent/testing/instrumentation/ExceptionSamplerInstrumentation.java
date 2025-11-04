/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ExceptionSamplerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.testing.internal.armeria.common.ExceptionSampler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // in the constructor of ExceptionSampler replace new NonBlockingHashMap with new
    // ConcurrentHashMap
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper.AbstractBase() {

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
                    return new ClassVisitor(Opcodes.ASM9, classVisitor) {
                      @Override
                      public MethodVisitor visitMethod(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          String[] exceptions) {
                        MethodVisitor mv =
                            super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!"<init>".equals(name)) {
                          return mv;
                        }

                        return new MethodVisitor(api, mv) {
                          @Override
                          public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW
                                && type.endsWith("jctools/maps/NonBlockingHashMap")) {
                              type = Type.getInternalName(ConcurrentHashMap.class);
                            }
                            super.visitTypeInsn(opcode, type);
                          }

                          @Override
                          public void visitMethodInsn(
                              int opcode,
                              String owner,
                              String name,
                              String descriptor,
                              boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL
                                && owner.endsWith("jctools/maps/NonBlockingHashMap")
                                && name.equals("<init>")) {
                              owner = Type.getInternalName(ConcurrentHashMap.class);
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                          }
                        };
                      }
                    };
                  }
                }));
  }
}
