/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures runtime metrics collection for Java 17+.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// we're not using "runtime-telemetry-java17" requirement here, because java17 is just
// an additional feature - which enables JFR metrics
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@Configuration
`@ConditionalOnJava(value = JavaVersion.SEVENTEEN, range = ConditionalOnJava.Range.EQUAL_OR_NEWER)`
public class Java17RuntimeMetricsAutoConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(Java17RuntimeMetricsAutoConfiguration.class);

  private Runnable shutdownHook;

  @PreDestroy
  public void stopMetrics() {
    if (shutdownHook != null) {
      shutdownHook.run();
    }
  }

  @Bean
  CommandLineRunner startMetrics(OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    return (args) -> {
      logger.debug("Use runtime metrics instrumentation for Java 17+");
      RuntimeMetrics.builder(openTelemetry)
          .setShutdownHook(runnable -> shutdownHook = runnable)
          .startFromInstrumentationConfig(new ConfigPropertiesBridge(configProperties));
    };
  }
}
