/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;

/** Helper class for transforming lambda class bytes using java9 api. */
public final class Java9LambdaTransformer {

  private Java9LambdaTransformer() {}

  public static byte[] transform(
      ClassFileTransformer transformer,
      byte[] classBytes,
      String slashClassName,
      Class<?> targetClass)
      throws IllegalClassFormatException {
    return transformer.transform(
        targetClass.getModule(),
        targetClass.getClassLoader(),
        slashClassName,
        null,
        null,
        classBytes);
  }
}
