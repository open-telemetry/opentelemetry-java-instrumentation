/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.bootstrap.LambdaTransformer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;

/** Lambda transformer for java versions before 9 without jpms modules support. */
public class Java8LambdaTransformer implements LambdaTransformer {

  private final ClassFileTransformer delegate;

  public Java8LambdaTransformer(ClassFileTransformer delegate) {
    this.delegate = delegate;
  }

  @Override
  public byte[] transform(String className, Class<?> targetClass, byte[] classfileBuffer)
      throws IllegalClassFormatException {

    // lambda instrumentation happens only when the lambda is being defined, so the targetClass
    // argument should not be passed to the transformer otherwise we get a partial instrumentation,
    // for example virtual fields are not properly applied
    return delegate.transform(targetClass.getClassLoader(), className, null, null, classfileBuffer);
  }
}
