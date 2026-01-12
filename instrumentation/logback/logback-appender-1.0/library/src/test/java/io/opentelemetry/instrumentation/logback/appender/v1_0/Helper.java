/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ContextBase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.LoggerFactory;

class Helper {

  static void resetLoggerContext() {
    try {
      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
      Field field = ContextBase.class.getDeclaredField("propertyMap");
      field.setAccessible(true);
      Map<?, ?> map = (Map<?, ?>) field.get(loggerContext);
      map.clear();

      Method method;
      try {
        method = LoggerContext.class.getDeclaredMethod("syncRemoteView");
      } catch (NoSuchMethodException noSuchMethodException) {
        method = LoggerContext.class.getDeclaredMethod("updateLoggerContextVO");
      }
      method.setAccessible(true);
      method.invoke(loggerContext);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to reset logger context", exception);
    }
  }

  private Helper() {}
}
