package datadog.trace.agent.tooling;

import datadog.trace.agent.bootstrap.ExceptionLogger;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandlers {
  private static final String LOG_FACTORY_NAME = LoggerFactory.class.getName().replace('.', '/');
  private static final String LOGGER_NAME = Logger.class.getName().replace('.', '/');
  // Bootstrap ExceptionHandler.class will always be resolvable, so we'll use it in the log name
  private static final String HANDLER_NAME = ExceptionLogger.class.getName().replace('.', '/');

  private static final StackManipulation EXCEPTION_STACK_HANDLER =
      new StackManipulation() {
        private final Size size = StackSize.SINGLE.toDecreasingSize();

        @Override
        public boolean isValid() {
          return true;
        }

        @Override
        public Size apply(MethodVisitor mv, Implementation.Context context) {
          // writes the following bytecode:
          // try {
          //   org.slf4j.LoggerFactory.getLogger((Class)ExceptionLogger.class).debug("exception in instrumentation", t);
          // } catch (Throwable t2) {
          // }
          Label logStart = new Label();
          Label logEnd = new Label();
          Label eatException = new Label();
          Label handlerExit = new Label();

          mv.visitTryCatchBlock(logStart, logEnd, eatException, "java/lang/Throwable");

          // stack: (top) throwable
          mv.visitLabel(logStart);
          mv.visitLdcInsn(Type.getType("L" + HANDLER_NAME + ";"));
          mv.visitMethodInsn(
              Opcodes.INVOKESTATIC,
              LOG_FACTORY_NAME,
              "getLogger",
              "(Ljava/lang/Class;)L" + LOGGER_NAME + ";",
              false);
          mv.visitInsn(Opcodes.SWAP); // stack: (top) throwable,logger
          mv.visitLdcInsn("Failed to handle exception in instrumentation");
          mv.visitInsn(Opcodes.SWAP); // stack: (top) throwable,string,logger
          mv.visitMethodInsn(
              Opcodes.INVOKEINTERFACE,
              LOGGER_NAME,
              "debug",
              "(Ljava/lang/String;Ljava/lang/Throwable;)V",
              true);
          mv.visitLabel(logEnd);
          mv.visitJumpInsn(Opcodes.GOTO, handlerExit);

          // if the runtime can't reach our ExceptionHandler or logger, silently eat the exception
          mv.visitLabel(eatException);
          mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
          mv.visitInsn(Opcodes.POP);
          // mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false);

          mv.visitLabel(handlerExit);
          mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

          return size;
        }
      };

  public static StackManipulation defaultExceptionHandler() {
    return EXCEPTION_STACK_HANDLER;
  }
}
