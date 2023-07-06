/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.Iterator;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@ConditionalOnClass({OpenTelemetryAppender.class, LoggerContext.class})
@AutoConfiguration
public class OtelLogbackAutoConfiguration {

  static class OtelInjectorForLogback implements BeanPostProcessor, Ordered {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      if (bean instanceof OpenTelemetry) {
        OpenTelemetry openTelemetry = (OpenTelemetry) bean;
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Optional<OpenTelemetryAppender> potentialOtelAppender =
            findOtelAppenderWithLogback(loggerContext);
        if (potentialOtelAppender.isPresent()) {
          OpenTelemetryAppender openTelemetryAppender = potentialOtelAppender.get();
          openTelemetryAppender.setOpenTelemetry(openTelemetry);
        }
      }
      return bean;
    }

    private static Optional<OpenTelemetryAppender> findOtelAppenderWithLogback(
        LoggerContext loggerContext) {
      for (Logger logger : loggerContext.getLoggerList()) {
        Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
          Appender<ILoggingEvent> appender = appenderIterator.next();
          if (appender instanceof OpenTelemetryAppender) {
            return Optional.of((OpenTelemetryAppender) appender);
          }
        }
      }
      return Optional.empty();
    }

    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE - 1;
    }
  }

  @Bean
  public OtelInjectorForLogback otelPostProcessor() {
    return new OtelInjectorForLogback();
  }
}
