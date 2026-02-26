/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimetelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configures runtime telemetry collection.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@Configuration
public class RuntimeMetricsAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(RuntimeMetricsAutoConfiguration.class);

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

    logger.debug("Start runtime telemetry instrumentation");
    this.closeable = Internal.configure(openTelemetry, instrumentationMode(openTelemetry));
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
