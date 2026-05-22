/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingStrategy;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingTarget;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import net.bytebuddy.utility.StreamDrainer;
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
  @Nullable
  private static ClassLoadingTarget getTarget(byte[] bytecode) {
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
    return null;
  }

  /**
   * Get class target with fallback on package target.
   *
   * @param className class name
   * @param classLoader class loader to load class/package bytecode
   * @return class loading target, or {@literal null} of no annotation is present
   */
  @Nullable
  public static ClassLoadingTarget getClassTarget(String className, ClassLoader classLoader) {
    String classPath = className.replace(".", "/") + ".class";
    byte[] byteCode = getByteCode(classPath, classLoader);
    if (byteCode == null) {
      // class is not present in this CL
      return null;
    }
    ClassLoadingTarget classTarget = getTarget(byteCode);
    if (null != classTarget) {
      return classTarget;
    }
    String packageName = className.substring(0, className.lastIndexOf('.'));
    ClassLoadingTarget packageTarget = packageTarget(packageName, classLoader);
    if (packageTarget != null) {
      return packageTarget;
    }
    return null;
  }

  // package-private for testing
  @Nullable
  static ClassLoadingTarget packageTarget(String packageName, ClassLoader classLoader) {
    String packagePath = packageName.replace(".", "/") + "/package-info.class";
    byte[] byteCode = getByteCode(packagePath, classLoader);
    return byteCode == null ? null : getTarget(byteCode);
  }

  // package-private for testing
  @Nullable
  static ClassLoadingTarget classTarget(String className, ClassLoader classLoader) {
    String classPath = className.replace(".", "/") + ".class";
    byte[] byteCode = getByteCode(classPath, classLoader);
    if (byteCode == null) {
      return null;
    }
    return getTarget(byteCode);
  }

  @Nullable
  private static byte[] getByteCode(String resourcePath, ClassLoader classLoader) {
    try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
      if (input == null) {
        return null;
      }
      return StreamDrainer.DEFAULT.drain(input);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
