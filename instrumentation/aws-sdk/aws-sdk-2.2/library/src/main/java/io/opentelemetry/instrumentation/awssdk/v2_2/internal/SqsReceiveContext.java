/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

final class SqsReceiveContext {

  @Nullable private static final Method isInternalListenerPoll = findInternalListenerPollMethod();

  static boolean isInternalListenerPoll() {
    if (isInternalListenerPoll == null) {
      return false;
    }
    try {
      return (boolean) isInternalListenerPoll.invoke(null);
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  @Nullable
  private static Method findInternalListenerPollMethod() {
    try {
      Class<?> contextClass =
          Class.forName(
              "io.opentelemetry.javaagent.bootstrap.InternalListenerPollContext", false, null);
      return contextClass.getMethod("isActive");
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private SqsReceiveContext() {}
}
