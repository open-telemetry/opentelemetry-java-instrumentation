/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.bootstrap.LambdaTransformer;
import io.opentelemetry.javaagent.bootstrap.LambdaTransformerHolder;

/** Helper class for transforming lambda class bytes. */
public final class LambdaTransformerHelper {

  private LambdaTransformerHelper() {}

  /**
   * Called from {@code java.lang.invoke.InnerClassLambdaMetafactory} to transform lambda class
   * bytes.
   */
  @SuppressWarnings("unused")
  public static byte[] transform(byte[] classBytes, String slashClassName, Class<?> targetClass) {
    // Skip transforming lambdas of injected helper classes.
    if (InjectedClassHelper.isHelperClass(targetClass)) {
      return classBytes;
    }
    LambdaTransformer transformer = LambdaTransformerHolder.getLambdaTransformer();
    if (transformer == null) {
      return classBytes;
    }

    try {
      byte[] result = transformer.transform(slashClassName, targetClass, classBytes);
      if (result != null) {
        classBytes = result;
      }
    } catch (Throwable throwable) {
      // sun.instrument.TransformerManager catches Throwable from ClassFileTransformer and ignores
      // it, we do the same.
    }
    return classBytes;
  }
}
