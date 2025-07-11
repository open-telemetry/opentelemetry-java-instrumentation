/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.instrument.IllegalClassFormatException;

/** Transformer for lambda bytecode */
public interface LambdaTransformer {

  /**
   * Transforms lambda bytecode for instrumentation
   *
   * @param className class name in JVM format with slashes
   * @param targetClass target class, must not be {@literal null}
   * @param classfileBuffer target class bytecode
   * @return instrumented lambda bytecode
   * @throws IllegalClassFormatException if bytecode is invalid
   */
  byte[] transform(String className, Class<?> targetClass, byte[] classfileBuffer)
      throws IllegalClassFormatException;
}
