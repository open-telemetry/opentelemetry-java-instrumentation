/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.RuntimeMetricsConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configures runtime metrics collection for Java 8+. This is a fallback configuration that only
 * activates when Java17RuntimeMetricsAutoConfiguration is not present (i.e., on Java versions older
 * than 17).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@ConditionalOnMissingBean(Java17RuntimeMetricsAutoConfiguration.class)
@Configuration
public class Java8RuntimeMetricsAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(Java8RuntimeMetricsAutoConfiguration.class);

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

    logger.debug("Use runtime metrics instrumentation for Java 8");
    this.closeable =
        RuntimeMetricsConfigUtil.configure(
            RuntimeMetrics.builder(openTelemetry), openTelemetry, true);
  }
}
