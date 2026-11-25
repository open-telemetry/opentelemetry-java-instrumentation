/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class CircuitBreakerFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.github.resilience4j.circuitbreaker.CircuitBreaker$CircuitBreakerFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    String helper =
        getClass().getPackageName().replace('.', '/') + "/Resilience4jCircuitBreakerSpans";
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper.ForDeclaredMethods()
                    .method(
                        named("get")
                            .and(takesArguments(0).or(takesArguments(long.class, TimeUnit.class))),
                        (type, method, visitor, context, pool, writerFlags, readerFlags) ->
                            new FutureGetVisitor(visitor, helper))
                    .writerFlags(ClassWriter.COMPUTE_MAXS)));
  }

  private static final class FutureGetVisitor extends MethodVisitor {
    private final String helper;

    private FutureGetVisitor(MethodVisitor visitor, String helper) {
      super(AsmApi.VERSION, visitor);
      this.helper = helper;
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (opcode == Opcodes.INVOKEVIRTUAL
          && owner.equals("io/github/resilience4j/core/functions/OnceConsumer")
          && name.equals("applyOnce")
          && descriptor.equals("(Ljava/util/function/Consumer;)V")) {
        super.visitVarInsn(Opcodes.ALOAD, 0);
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            helper,
            "applyFutureConsumer",
            "(Lio/github/resilience4j/core/functions/OnceConsumer;Ljava/util/function/Consumer;"
                + "Ljava/util/concurrent/Future;)V",
            false);
        return;
      }
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
  }
}
