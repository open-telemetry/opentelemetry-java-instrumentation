/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

final class BodySizeUtil {
  @Nullable private static final Class<?> messageLiteClass = getMessageLiteClass();

  @Nullable
  private static final Method serializedSizeMethod =
      messageLiteClass != null ? getSerializedSizeMethod(messageLiteClass) : null;

  private static Class<?> getMessageLiteClass() {
    try {
      return Class.forName("com.google.protobuf.MessageLite");
    } catch (Exception ignore) {
      return null;
    }
  }

  private static Method getSerializedSizeMethod(Class<?> clazz) {
    try {
      return clazz.getMethod("getSerializedSize");
    } catch (NoSuchMethodException ignore) {
      return null;
    }
  }

  static <T> Long getBodySize(T message) {
    if (messageLiteClass == null || serializedSizeMethod == null) {
      return null;
    }
    if (!messageLiteClass.isInstance(message)) {
      return null;
    }
    try {
      return ((Integer) serializedSizeMethod.invoke(message)).longValue();
    } catch (Throwable ignore) {
      return null;
    }
  }

  private BodySizeUtil() {}
}
