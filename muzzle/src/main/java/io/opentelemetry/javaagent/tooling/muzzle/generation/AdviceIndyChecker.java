/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.generation;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import io.opentelemetry.javaagent.tooling.muzzle.Utils;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Utility class that checks if an advice has known incompatibility with indy by analyzing its
 * bytecode.
 */
class AdviceIndyChecker {

  private AdviceIndyChecker() {}

  static class IndyCheckResult {
    private final String className;
    private String methodName;
    private String cause;

    private IndyCheckResult(String className) {
      this.className = className;
      this.cause = null;
    }

    /**
     * Returns true when the advice has known incompatibilities with indy.
     *
     * @return advice incompatibility with indy
     */
    boolean isIndyIncompatible() {
      return cause != null;
    }

    /**
     * Provides human-friendly description of incompatibility, if there is any.
     *
     * @return incompatibility description or null if there is none
     */
    @Nullable
    String getMessage() {
      if (cause == null) {
        return null;
      }
      return String.format("advice method %s.%s : %s", className, methodName, cause);
    }

    private void markIncompatible(String cause) {
      // only consider the first cause of incompatibility
      if (this.cause == null) {
        this.cause = cause;
      }
    }

    private void setMethodName(String methodName) {
      this.methodName = methodName;
    }
  }

  static IndyCheckResult checkIndyAdvice(ClassLoader classLoader, String adviceClassName) {
    try (InputStream in = Utils.getClassFileStream(classLoader, adviceClassName)) {
      ClassReader reader = new ClassReader(in);
      IndyCheckResult result = new IndyCheckResult(adviceClassName);
      AdviceClassVisitor classVisitor = new AdviceClassVisitor(result);
      reader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("unable to load advice class " + adviceClassName, e);
    }
  }

  private static class AdviceClassVisitor extends ClassVisitor {

    private final IndyCheckResult result;

    private AdviceClassVisitor(IndyCheckResult result) {
      super(AsmApi.VERSION);
      this.result = result;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if ((access & Opcodes.ACC_PUBLIC) > 0 && (access & Opcodes.ACC_STATIC) > 0) {
        result.setMethodName(name);
        return new AdviceMethodVisitor(result);
      }
      return null;
    }
  }

  private static class AdviceMethodVisitor extends MethodVisitor {
    private static final Type VIRTUAL_FIELD_TYPE = Type.getType(VirtualField.class);
    private final IndyCheckResult result;

    private AdviceMethodVisitor(IndyCheckResult result) {
      super(AsmApi.VERSION);
      this.result = result;
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {

      if (opcode == Opcodes.INVOKESTATIC
          && VIRTUAL_FIELD_TYPE.getInternalName().equals(owner)
          && "find".equals(name)) {
        result.markIncompatible(
            "advice contains call to VirtualField.find, which is not supported in indy advice");
      }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(
        int parameter, String descriptor, boolean visible) {

      if (descriptor.equals(Type.getDescriptor(Advice.Local.class))) {
        result.markIncompatible(
            "parameter annotated with Advice.Local, which is not supported in indy advice");
      } else if (descriptor.equals(Type.getDescriptor(Advice.Return.class))
          || descriptor.equals(Type.getDescriptor(Advice.Argument.class))) {
        return new AdviceAnnotationVisitor(result);
      }

      return null;
    }
  }

  private static class AdviceAnnotationVisitor extends AnnotationVisitor {
    private final IndyCheckResult result;

    private AdviceAnnotationVisitor(IndyCheckResult result) {
      super(AsmApi.VERSION);
      this.result = result;
    }

    @Override
    public void visit(String name, Object value) {
      if ("readOnly".equals(name) && Boolean.FALSE.equals(value)) {
        result.markIncompatible(
            "advice method parameter with @Argument(readOnly=false) or @Return(readOnly=false)");
      }
    }
  }
}
