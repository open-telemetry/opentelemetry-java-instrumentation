/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/** {@link ClassFileTransformer} implementation that provides java9 jpms module compatibility */
public class JavaModuleClassFileTransformer implements ClassFileTransformer {

  private ClassFileTransformer delegate;

  public JavaModuleClassFileTransformer(ClassFileTransformer delegate) {
    this.delegate = delegate;
  }

  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> targetClass,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer)
      throws IllegalClassFormatException {

    Module module = targetClass != null ? targetClass.getModule() : null;
    return delegate.transform(
        module, loader, className, targetClass, protectionDomain, classfileBuffer);
  }
}
