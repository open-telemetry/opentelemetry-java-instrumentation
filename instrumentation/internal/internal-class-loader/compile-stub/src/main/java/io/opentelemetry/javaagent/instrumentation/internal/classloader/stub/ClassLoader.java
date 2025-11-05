/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader.stub;

import java.security.ProtectionDomain;

/**
 * A placeholder for java.lang.ClassLoader to allow compilation of advice classes that invoke
 * protected methods of ClassLoader (like defineClass and findLoadedClass). During the build we'll
 * use shadow plugin to replace reference to this class with the real java.lang.ClassLoader.
 */
@SuppressWarnings("JavaLangClash")
public abstract class ClassLoader {
  public abstract Class<?> findLoadedClass(String name);

  public abstract Class<?> defineClass(
      String name, byte[] b, int off, int len, ProtectionDomain protectionDomain);
}
