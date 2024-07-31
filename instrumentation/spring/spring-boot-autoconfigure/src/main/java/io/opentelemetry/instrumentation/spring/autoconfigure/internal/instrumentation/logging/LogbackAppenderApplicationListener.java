/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class LogbackAppenderApplicationListener implements GenericApplicationListener {

  private static final Class<?>[] SOURCE_TYPES = {
    SpringApplication.class, ApplicationContext.class
  };
  private static final Class<?>[] EVENT_TYPES = {ApplicationEnvironmentPreparedEvent.class};
  private static final boolean LOGBACK_PRESENT = isLogbackPresent();

  @Override
  public boolean supportsSourceType(Class<?> sourceType) {
    return isAssignableFrom(sourceType, SOURCE_TYPES);
  }

  @Override
  public boolean supportsEventType(ResolvableType resolvableType) {
    return isAssignableFrom(resolvableType.getRawClass(), EVENT_TYPES);
  }

  private static boolean isAssignableFrom(Class<?> type, Class<?>... supportedTypes) {
    if (type != null) {
      for (Class<?> supportedType : supportedTypes) {
        if (supportedType.isAssignableFrom(type)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (!LOGBACK_PRESENT) {
      return;
    }

    // Event for which org.springframework.boot.context.logging.LoggingApplicationListener
    // initializes logging
    if (event instanceof ApplicationEnvironmentPreparedEvent) {
      LogbackAppenderInstaller.install((ApplicationEnvironmentPreparedEvent) event);
    }
  }

  @Override
  public int getOrder() {
    return LoggingApplicationListener.DEFAULT_ORDER + 1; // To execute this listener just after
    // org.springframework.boot.context.logging.LoggingApplicationListener
  }

  private static boolean isLogbackPresent() {
    try {
      Class.forName("ch.qos.logback.core.Appender");
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }
}
