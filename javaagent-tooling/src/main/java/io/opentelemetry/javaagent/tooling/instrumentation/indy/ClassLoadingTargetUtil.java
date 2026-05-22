/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingStrategy;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingTarget;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Utility to help with class-loading indy modules */
public class ClassLoadingTargetUtil {

  private static final Type STRATEGY_ANNOTATION = Type.getType(ClassLoadingStrategy.class);
  private static final Type TARGET_ENUM = Type.getType(ClassLoadingTarget.class);

  private ClassLoadingTargetUtil() {}

  /**
   * Reads the class class-loading strategy from class (or package) bytecode
   *
   * @param bytecode class or package bytecode
   * @return class loading strategy, defaults to {@link ClassLoadingTarget#INSTRUMENTATION_ISOLATED}
   *     if annotation is not present.
   */
  // package-protected for testing
  static ClassLoadingTarget getTarget(byte[] bytecode) {
    ClassReader cr = new ClassReader(bytecode);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    if (classNode.visibleAnnotations != null) {
      for (AnnotationNode annotation : classNode.visibleAnnotations) {
        if (Type.getType(annotation.desc).equals(STRATEGY_ANNOTATION)) {
          for (Object value : annotation.values) {
            if (value instanceof String[]) {
              String[] array = (String[]) value;
              if (array.length == 2 && Type.getType(array[0]).equals(TARGET_ENUM)) {
                return ClassLoadingTarget.valueOf(array[1]);
              }
            }
          }
        }
      }
    }
    return ClassLoadingTarget.INSTRUMENTATION_ISOLATED;
  }

  public static ClassLoadingTarget getTarget(String className, ClassLoader classLoader) {
    return null;
  }
}
