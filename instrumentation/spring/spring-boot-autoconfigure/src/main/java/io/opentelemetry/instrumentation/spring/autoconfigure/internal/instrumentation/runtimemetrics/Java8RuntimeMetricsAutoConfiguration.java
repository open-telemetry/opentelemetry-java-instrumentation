/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Configures runtime metrics collection for Java 8.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "runtime-telemetry")
@Configuration
@Conditional(Java8RuntimeMetricsAutoConfiguration.Java8MetricsCondition.class)
public class Java8RuntimeMetricsAutoConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(Java8RuntimeMetricsAutoConfiguration.class);

  private Runnable shutdownHook;

  @PreDestroy
  public void stopMetrics() {
    if (shutdownHook != null) {
      shutdownHook.run();
    }
  }

  @Bean
  CommandLineRunner startMetricsForJava8(
      OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    return (args) -> {
      logger.debug("Use runtime metrics instrumentation for Java 8");
      RuntimeMetrics.builder(openTelemetry)
          .setShutdownHook(runnable -> shutdownHook = runnable)
          .startFromInstrumentationConfig(new ConfigPropertiesBridge(configProperties));
    };
  }

  static class Java8MetricsCondition extends AnyNestedCondition {
    public Java8MetricsCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnJava(value = JavaVersion.SEVENTEEN, range = ConditionalOnJava.Range.OLDER_THAN)
    static class OlderThanJava17 {}

    // GraalVM native image does not support Java 17 metrics yet
    @ConditionalOnProperty("org.graalvm.nativeimage.imagecode")
    static class GraalVm {}
  }
}
