/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.nio.ByteBuffer;

public class DefineClassHelper {

  /** Helper class for {@code ClassLoader.defineClass} callbacks. */
  public interface Handler {
    DefineClassContext beforeDefineClass(
        ClassLoader classLoader, String className, byte[] classBytes, int offset, int length);

    void afterDefineClass(DefineClassContext context);

    /** Context returned from {@code beforeDefineClass} and passed to {@code afterDefineClass}. */
    interface DefineClassContext {
      void exit();
    }
  }

  private static volatile Handler handler;

  public static Handler.DefineClassContext beforeDefineClass(
      ClassLoader classLoader, String className, byte[] classBytes, int offset, int length) {
    return handler.beforeDefineClass(classLoader, className, classBytes, offset, length);
  }

  public static Handler.DefineClassContext beforeDefineClass(
      ClassLoader classLoader, String className, ByteBuffer byteBuffer) {
    // see how ClassLoader handles ByteBuffer
    // https://github.com/openjdk/jdk11u/blob/487c3344fee3502b4843e7e11acceb77ad16100c/src/java.base/share/classes/java/lang/ClassLoader.java#L1095
    int length = byteBuffer.remaining();
    if (byteBuffer.hasArray()) {
      return beforeDefineClass(
          classLoader,
          className,
          byteBuffer.array(),
          byteBuffer.position() + byteBuffer.arrayOffset(),
          length);
    } else {
      byte[] classBytes = new byte[length];
      byteBuffer.duplicate().get(classBytes);
      return beforeDefineClass(classLoader, className, classBytes, 0, length);
    }
  }

  public static void afterDefineClass(Handler.DefineClassContext context) {
    handler.afterDefineClass(context);
  }

  /**
   * Sets the {@link Handler} with callbacks to execute when {@code ClassLoader.defineClass} is
   * called.
   */
  public static void internalSetHandler(Handler handler) {
    if (DefineClassHelper.handler != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    DefineClassHelper.handler = handler;
  }

  private DefineClassHelper() {}
}
