/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

@SuppressWarnings("unused")
public final class DefineClassUtil {
  private DefineClassUtil() {}

  /**
   * Handle LinkageError in ClassLoader.defineClass. Call to this method is inserted into
   * ClassLoader.defineClass by DefineClassInstrumentation.
   *
   * @param linkageError LinkageError that happened in defineClass
   * @param helpersInjected whether helpers were injected during defineClass call
   * @param clazz Class that is being defined if it is already loaded
   * @return give Class if LinkageError was a duplicate class definition error
   */
  public static Class<?> handleLinkageError(
      LinkageError linkageError, boolean helpersInjected, Class<?> clazz) {
    // only attempt to recover from duplicate class definition if helpers were injected during
    // the defineClass call
    if (!helpersInjected
        // if exception was duplicate class definition we'll have access to the loaded class
        || clazz == null
        // duplicate class definition throws LinkageError, we can ignore its subclasses
        || linkageError.getClass() != LinkageError.class) {
      throw linkageError;
    }
    // check that the exception is a duplicate class or interface definition
    String message = linkageError.getMessage();
    if (message == null
        || !(message.contains("duplicate interface definition")
            || message.contains("duplicate class definition"))) {
      throw linkageError;
    }
    return clazz;
  }
}
