/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal.classloader.stub;

import java.security.ProtectionDomain;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 *
 * <p>A placeholder for java.lang.ClassLoader to allow compilation of advice classes that invoke
 * protected methods of ClassLoader (like defineClass and findLoadedClass). During the build we'll
 * use shadow plugin to replace reference to this class with the real java.lang.ClassLoader.
 *
 * <p>This class is in {@code io.opentelemetry.javaagent.bootstrap} package rather than {@code
 * io.opentelemetry.javaagent.instrumentation} to avoid getting it added as a helper class. Since
 * during build references to this class are renamed to {@code java.lang.ClassLoader} having it
 * added as helper would really get {@code java.lang.ClassLoader} added as helper which breaks some
 * tests.
 */
@SuppressWarnings("JavaLangClash")
public abstract class ClassLoader {
  public abstract Class<?> findLoadedClass(String name);

  public abstract Class<?> defineClass(
      String name, byte[] b, int off, int len, ProtectionDomain protectionDomain);
}
