/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import io.opentelemetry.javaagent.bootstrap.ClassFileTransformerHolder;
import java.lang.instrument.ClassFileTransformer;

/** Helper class for transforming lambda class bytes. */
public final class LambdaTransformer {

  private LambdaTransformer() {}

  /**
   * Called from {@code java.lang.invoke.InnerClassLambdaMetafactory} to transform lambda class
   * bytes.
   */
  public static byte[] transform(byte[] classBytes, String slashClassName, Class<?> targetClass) {
    ClassFileTransformer transformer = ClassFileTransformerHolder.getClassFileTransformer();
    if (transformer != null) {
      try {
        byte[] result =
            transformer.transform(
                targetClass.getClassLoader(), slashClassName, null, null, classBytes);
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
