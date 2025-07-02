/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Comparator;
import java.util.Optional;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configures runtime metrics collection.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@Configuration
public class RuntimeMetricsAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(RuntimeMetricsAutoConfiguration.class);

  private AutoCloseable closeable;

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
    ConfigPropertiesBridge config =
        new ConfigPropertiesBridge(applicationContext.getBean(ConfigProperties.class));

    double version =
        Math.max(8, Double.parseDouble(System.getProperty("java.specification.version")));
    Optional<RuntimeMetricsProvider> metricsProvider =
        applicationContext.getBeanProvider(RuntimeMetricsProvider.class).stream()
            .sorted(Comparator.comparing(RuntimeMetricsProvider::minJavaVersion).reversed())
            .filter(provider -> provider.minJavaVersion() <= version)
            .findFirst();

    if (metricsProvider.isPresent()) {
      this.closeable = metricsProvider.get().start(openTelemetry, config);
    } else {
      logger.debug("No runtime metrics instrumentation available for Java {}", version);
    }
  }
}
