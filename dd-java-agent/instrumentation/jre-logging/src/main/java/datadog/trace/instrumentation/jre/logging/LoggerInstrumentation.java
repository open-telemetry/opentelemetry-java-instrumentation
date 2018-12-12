package datadog.trace.instrumentation.jre.logging;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.ClassLoaderMatcher;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * This instrumentation patches java.util.logging.Logger to return a "safe" logger which doesn't
 * touch the global log manager when static log creators (e.g. getLogger) are invoked under datadog
 * threads.
 *
 * <p>Our PatchLogger solution is not enough here because it's possible for dd-threads to use
 * bootstrap utils which in turn use call Logger.getLogger(...)
 */
@Slf4j
@AutoService(Instrumenter.class)
public class LoggerInstrumentation implements Instrumenter {
  // Intentionally doing the string replace to bypass gradle shadow rename
  // loggerClassName = java.util.logging.Logger
  private static final String loggerClassName =
      "java.util.logging.TMP".replaceFirst("TMP", "Logger");

  public LoggerInstrumentation() {}

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            failSafe(
                named(loggerClassName),
                "Instrumentation type matcher unexpected exception: " + getClass().getName()),
            failSafe(
                new ElementMatcher<ClassLoader>() {
                  @Override
                  public boolean matches(ClassLoader target) {
                    return target == ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
                  }
                },
                "Instrumentation class loader matcher unexpected exception: "
                    + getClass().getName()))
        .transform(
            new AgentBuilder.Transformer() {
              @Override
              public DynamicType.Builder<?> transform(
                  DynamicType.Builder<?> builder,
                  TypeDescription typeDescription,
                  ClassLoader classLoader,
                  JavaModule module) {
                return builder.visit(new ReturnPatchLoggerForDDThreadsVisitor());
              }
            });
  }

  /**
   * Replace java.util.logging.Logger#getLogger() methods with an early-return for datadog threads
   * to avoid initializing the global log manager.
   */
  private static class ReturnPatchLoggerForDDThreadsVisitor implements AsmVisitorWrapper {
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
      return new ClassVisitor(Opcodes.ASM7, classVisitor) {
        @Override
        public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
          super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(
            int access,
            final String methodName,
            final String methodDesc,
            String signature,
            String[] exceptions) {
          final MethodVisitor mv =
              super.visitMethod(access, methodName, methodDesc, signature, exceptions);
          // if isStatic and returns a logger, then add our advice
          if ((access & Opcodes.ACC_STATIC) != 0) {
            final Type returnType = Type.getReturnType(methodDesc);
            if (returnType != null
                && returnType.getSort() == Type.OBJECT
                && returnType.getInternalName().equals(Utils.getInternalName(loggerClassName))) {
              return new MethodVisitor(Opcodes.ASM7, mv) {
                @Override
                public void visitCode() {
                  /* Appends an early return to the method body for datadog threads:
                   *
                   * if (Thread.currentThread().getName().startsWith("dd-")) {
                   *    // intentionally invoke private constructor to avoid initializing the LogManager
                   *   return new Logger("datadog");
                   * }
                   * // original method body
                   */
                  final Label originalMethodBody = new Label();
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "java/lang/Thread",
                      "currentThread",
                      "()Ljava/lang/Thread;",
                      false);
                  mv.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      "java/lang/Thread",
                      "getName",
                      "()Ljava/lang/String;",
                      false);
                  mv.visitLdcInsn("dd-");
                  mv.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      "java/lang/String",
                      "startsWith",
                      "(Ljava/lang/String;)Z",
                      false);
                  mv.visitJumpInsn(Opcodes.IFEQ, originalMethodBody);
                  mv.visitTypeInsn(Opcodes.NEW, Utils.getInternalName(loggerClassName));
                  mv.visitInsn(Opcodes.DUP);
                  mv.visitLdcInsn("datadog-logger");
                  mv.visitMethodInsn(
                      Opcodes.INVOKESPECIAL,
                      Utils.getInternalName(loggerClassName),
                      "<init>",
                      "(Ljava/lang/String;)V",
                      false);
                  mv.visitInsn(Opcodes.ARETURN);
                  mv.visitLabel(originalMethodBody);
                  mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                  super.visitCode();
                }
              };
            }
          }
          return mv;
        }
      };
    }
  }
}
