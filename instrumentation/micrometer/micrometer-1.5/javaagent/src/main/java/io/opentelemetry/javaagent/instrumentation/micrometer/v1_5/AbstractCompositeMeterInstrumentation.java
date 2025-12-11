/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.util.Collection;
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

public class AbstractCompositeMeterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.micrometer.core.instrument.composite.AbstractCompositeMeter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper() {
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
                    return new ClassVisitor(AsmApi.VERSION, classVisitor) {
                      @Override
                      public MethodVisitor visitMethod(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          String[] exceptions) {
                        MethodVisitor mv =
                            super.visitMethod(access, name, descriptor, signature, exceptions);
                        if ("firstChild".equals(name)) {
                          return new MethodVisitor(api, mv) {
                            @Override
                            public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface) {
                              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                              if (Opcodes.INVOKEINTERFACE == opcode
                                  && Type.getInternalName(Collection.class).equals(owner)
                                  && "iterator".equals(name)
                                  && "()Ljava/util/Iterator;".equals(descriptor)) {
                                // wrap the returned iterator to filter out our MeterRegistry
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    Type.getInternalName(MicrometerSingletons.class),
                                    "wrapIterator",
                                    "(Ljava/util/Iterator;)Ljava/util/Iterator;",
                                    false);
                              }
                            }
                          };
                        }
                        return mv;
                      }
                    };
                  }
                }));
  }
}
