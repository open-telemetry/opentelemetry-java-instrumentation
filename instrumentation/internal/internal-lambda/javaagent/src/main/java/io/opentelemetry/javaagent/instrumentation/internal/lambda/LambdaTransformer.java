/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import io.opentelemetry.javaagent.bootstrap.ClassFileTransformerHolder;
import java.lang.instrument.ClassFileTransformer;

/** Helper class for transforming lambda class bytes. */
public final class LambdaTransformer {
  private static final boolean IS_JAVA_9 = isJava9();

  private LambdaTransformer() {}

  private static boolean isJava9() {
    try {
      Class.forName("java.lang.Module", false, null);
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  /**
   * Called from {@code java.lang.invoke.InnerClassLambdaMetafactory} to transform lambda class
   * bytes.
   */
  @SuppressWarnings("unused")
  public static byte[] transform(byte[] classBytes, String slashClassName, Class<?> targetClass) {
    ClassFileTransformer transformer = ClassFileTransformerHolder.getClassFileTransformer();
    if (transformer != null) {
      try {
        byte[] result;
        if (IS_JAVA_9) {
          result =
              Java9LambdaTransformer.transform(
                  transformer, classBytes, slashClassName, targetClass);
        } else {
          result =
              transformer.transform(
                  targetClass.getClassLoader(), slashClassName, null, null, classBytes);
        }
        if (result != null) {
          return result;
        }
      } catch (Throwable throwable) {
        // sun.instrument.TransformerManager catches Throwable from ClassFileTransformer and ignores
        // it, we do the same.
      }
    }

    return classBytes;
  }
}
