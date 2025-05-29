/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.payara;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StandardWrapperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.catalina.core.StandardWrapper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper() {
                  @Override
                  public int mergeWriter(int flags) {
                    return flags | ClassWriter.COMPUTE_MAXS;
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
                    return new StandardWrapperClassVisitor(classVisitor);
                  }
                }));
  }

  private static class StandardWrapperClassVisitor extends ClassVisitor {

    StandardWrapperClassVisitor(ClassVisitor cv) {
      super(AsmApi.VERSION, cv);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("service".equals(name)
          && ("(Ljakarta/servlet/ServletRequest;Ljakarta/servlet/ServletResponse;Ljakarta/servlet/Servlet;)V"
                  .equals(descriptor)
              || "(Ljavax/servlet/ServletRequest;Ljavax/servlet/ServletResponse;Ljavax/servlet/Servlet;)V"
                  .equals(descriptor))) {
        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Make call to activeSpan return null to prevent payara from closing our server
                // span
                // https://github.com/payara/Payara/blob/0369a7a9f724217e313d965902c03e06ea73f266/appserver/web/web-core/src/main/java/org/apache/catalina/core/StandardWrapper.java#L1576
                if ("activeSpan".equals(name)
                    && "io/opentracing/Tracer".equals(owner)
                    && "()Lio/opentracing/Span;".equals(descriptor)) {
                  mv.visitInsn(Opcodes.POP);
                  mv.visitInsn(Opcodes.ACONST_NULL);
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              }
            };
      }
      return mv;
    }
  }
}
