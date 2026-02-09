/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RuntimeMetricsConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava.Range;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configures runtime metrics collection for Java 17+.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@ConditionalOnJava(range = Range.EQUAL_OR_NEWER, value = JavaVersion.SEVENTEEN)
@Configuration
public class Java17RuntimeMetricsAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(Java17RuntimeMetricsAutoConfiguration.class);

  @Nullable private AutoCloseable closeable;

  @PreDestroy
  public void stopMetrics() throws Exception {
    if (closeable != null) {
      closeable.close();
    }
  }

  @EventListener
  public void handleApplicationReadyEvent(ApplicationReadyEvent event) {
    ConfigurableApplicationContext applicationContext = event.getApplicationContext();
    OpenTelemetry openTelemetry = applicationContext.getBean(OpenTelemetry.class);

    logger.debug("Use runtime metrics instrumentation for Java 17+");
    this.closeable =
        RuntimeMetricsConfigUtil.configure(
            RuntimeMetrics.builder(openTelemetry),
            openTelemetry,
            instrumentationMode(openTelemetry));
  }

  private static String instrumentationMode(OpenTelemetry openTelemetry) {
    String mode =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "spring_starter")
            .getString("instrumentation_mode", "default");
    if (!mode.equals("default") && !mode.equals("none")) {
      throw new ConfigurationException("Unknown instrumentation mode: " + mode);
    }
    return mode;
  }
}
