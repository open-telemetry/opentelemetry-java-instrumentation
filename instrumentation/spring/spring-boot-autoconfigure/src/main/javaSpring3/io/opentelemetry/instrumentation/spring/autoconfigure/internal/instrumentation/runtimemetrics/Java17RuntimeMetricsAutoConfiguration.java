/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RuntimeMetricsConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
@Configuration
@ConditionalOnJava(JavaVersion.SEVENTEEN)
@ConditionalOnClass(RuntimeMetrics.class)
public class Java17RuntimeMetricsAutoConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(Java17RuntimeMetricsAutoConfiguration.class);

  static final String JAVA_17_RUNTIME_METRICS_PROVIDER_BEAN_NAME = "java17RuntimeMetricsProvider";

  @Bean(name = JAVA_17_RUNTIME_METRICS_PROVIDER_BEAN_NAME)
  RuntimeMetricsProvider java17RuntimeMetricsProvider() {
    return new RuntimeMetricsProvider() {
      @Override
      public int minJavaVersion() {
        return 17;
      }

      @Override
      public AutoCloseable start(OpenTelemetry openTelemetry, InstrumentationConfig config) {
        logger.debug("Use runtime metrics instrumentation for Java 17+");
        return RuntimeMetricsConfigUtil.configure(RuntimeMetrics.builder(openTelemetry), config);
      }
    };
  }
}
