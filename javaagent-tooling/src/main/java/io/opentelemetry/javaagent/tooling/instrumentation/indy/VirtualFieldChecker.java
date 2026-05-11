/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Check that advice does not call {@link VirtualField#find(Class, Class)}. For inline advice {@link
 * VirtualField#find(Class, Class)} calls in advice are rewritten for efficiency. In non-inline
 * advice we don't do such rewriting, we expect users to keep the result of {@link
 * VirtualField#find(Class, Class)} in a static field.
 */
class VirtualFieldChecker {

  private static final Type VIRTUAL_FIELD_TYPE = Type.getType(VirtualField.class);
  private static final Type ADVICE_ON_METHOD_ENTER = Type.getType(Advice.OnMethodEnter.class);
  private static final Type ADVICE_ON_METHOD_EXIT = Type.getType(Advice.OnMethodExit.class);

  static void check(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

    String dotClassName = Type.getObjectType(classNode.name).getClassName();
    classNode.methods.forEach(m -> checkMethod(m, dotClassName));
  }

  private static void checkMethod(MethodNode methodNode, String dotClassName) {
    if (!hasAnnotation(methodNode, ADVICE_ON_METHOD_ENTER)
        && !hasAnnotation(methodNode, ADVICE_ON_METHOD_EXIT)) {
      return;
    }

    methodNode.accept(
        new MethodVisitor(AsmApi.VERSION, null) {
          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC
                && VIRTUAL_FIELD_TYPE.getInternalName().equals(owner)
                && "find".equals(name)) {
              throw new IllegalStateException(
                  "Found usage of VirtualField.find in advice "
                      + dotClassName
                      + "."
                      + methodNode.name);
            }
          }
        });
  }

  @Nullable
  private static AnnotationNode getAnnotationNode(MethodNode source, Type type) {
    if (source.visibleAnnotations != null) {
      for (AnnotationNode annotationNode : source.visibleAnnotations) {
        Type annotationType = Type.getType(annotationNode.desc);
        if (type.equals(annotationType)) {
          return annotationNode;
        }
      }
    }

    return null;
  }

  private static boolean hasAnnotation(MethodNode source, Type type) {
    return getAnnotationNode(source, type) != null;
  }

  private VirtualFieldChecker() {}
}
