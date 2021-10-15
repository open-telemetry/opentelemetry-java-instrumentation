/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.Advice.ExceptionHandler;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExceptionHandlers {
  private static final String LOG_FACTORY_NAME = LoggerFactory.class.getName().replace('.', '/');
  private static final String LOGGER_NAME = Logger.class.getName().replace('.', '/');
  // Bootstrap ExceptionHandler.class will always be resolvable, so we'll use it in the log name
  private static final String HANDLER_NAME = ExceptionLogger.class.getName().replace('.', '/');

  private static final ExceptionHandler EXCEPTION_STACK_HANDLER =
      new ExceptionHandler.Simple(
          new StackManipulation() {
            // Pops one Throwable off the stack. Maxes the stack to at least 3.
            private final StackManipulation.Size size = new StackManipulation.Size(-1, 3);

            @Override
            public boolean isValid() {
              return true;
            }

            @Override
            public StackManipulation.Size apply(MethodVisitor mv, Implementation.Context context) {
              String name = context.getInstrumentedType().getName();
              ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

              // writes the following bytecode:
              // try {
              //   org.slf4j.LoggerFactory.getLogger((Class)ExceptionLogger.class)
              //     .debug("exception in instrumentation", t);
              // } catch (Throwable t2) {
              // }
              Label logStart = new Label();
              Label logEnd = new Label();
              Label eatException = new Label();
              Label handlerExit = new Label();

              // Frames are only meaningful for class files in version 6 or later.
              boolean frames = context.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6);

              mv.visitTryCatchBlock(logStart, logEnd, eatException, "java/lang/Throwable");

              // stack: (top) throwable
              mv.visitLabel(logStart);
              mv.visitLdcInsn(Type.getType("L" + HANDLER_NAME + ";"));
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  LOG_FACTORY_NAME,
                  "getLogger",
                  "(Ljava/lang/Class;)L" + LOGGER_NAME + ";",
                  /* isInterface= */ false);
              mv.visitInsn(Opcodes.SWAP); // stack: (top) throwable,logger
              mv.visitLdcInsn(
                  "Failed to handle exception in instrumentation for "
                      + name
                      + " on "
                      + classLoader);
              mv.visitInsn(Opcodes.SWAP); // stack: (top) throwable,string,logger
              mv.visitMethodInsn(
                  Opcodes.INVOKEINTERFACE,
                  LOGGER_NAME,
                  "debug",
                  "(Ljava/lang/String;Ljava/lang/Throwable;)V",
                  /* isInterface= */ true);
              mv.visitLabel(logEnd);
              mv.visitJumpInsn(Opcodes.GOTO, handlerExit);

              // if the runtime can't reach our ExceptionHandler or logger,
              //   silently eat the exception
              mv.visitLabel(eatException);
              if (frames) {
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
              }
              mv.visitInsn(Opcodes.POP);
              // mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
              //    "printStackTrace", "()V", false);

              mv.visitLabel(handlerExit);
              if (frames) {
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
              }

              return size;
            }
          });

  public static ExceptionHandler defaultExceptionHandler() {
    return EXCEPTION_STACK_HANDLER;
  }

  private ExceptionHandlers() {}
}
