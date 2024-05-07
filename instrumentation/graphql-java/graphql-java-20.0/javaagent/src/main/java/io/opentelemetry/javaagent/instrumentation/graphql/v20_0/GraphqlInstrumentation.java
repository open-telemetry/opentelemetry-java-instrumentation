/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import static io.opentelemetry.javaagent.instrumentation.graphql.v20_0.GraphqlSingletons.addInstrumentation;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
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

class GraphqlInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("graphql.GraphQL");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), this.getClass().getName() + "$AddInstrumentationAdvice");

    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper() {
                  @Override
                  public int mergeWriter(int flags) {
                    return flags;
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
                        if ("<init>".equals(name)
                            && "(Lgraphql/GraphQL$Builder;)V".equals(descriptor)) {
                          return new MethodVisitor(api, mv) {
                            @Override
                            public void visitFieldInsn(
                                int opcode, String owner, String name, String descriptor) {
                              // Call GraphqlSingletons.addInstrumentation on the value before it is
                              // written to the instrumentation field
                              if (opcode == Opcodes.PUTFIELD
                                  && "instrumentation".equals(name)
                                  && "Lgraphql/execution/instrumentation/Instrumentation;"
                                      .equals(descriptor)) {
                                mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    Type.getInternalName(GraphqlSingletons.class),
                                    "addInstrumentation",
                                    "(Lgraphql/execution/instrumentation/Instrumentation;)Lgraphql/execution/instrumentation/Instrumentation;",
                                    false);
                              }
                              super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                          };
                        }
                        return mv;
                      }
                    };
                  }
                }));
  }

  @SuppressWarnings("unused")
  public static class AddInstrumentationAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Instrumentation instrumentation) {
      // this advice is here only to get GraphqlSingletons injected and checked by muzzle
      instrumentation = addInstrumentation(instrumentation);
    }
  }
}
