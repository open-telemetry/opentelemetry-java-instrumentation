/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RuntimeMetricsConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EnabledInstrumentations;
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
    EnabledInstrumentations enabledInstrumentations =
        EarlyConfig.getEnabledInstrumentations(applicationContext.getEnvironment());

    logger.debug("Use runtime metrics instrumentation for Java 17+");
    RuntimeMetricsBuilder builder = RuntimeMetrics.builder(openTelemetry);

    if (RuntimeMetricsConfigUtil.allEnabled(openTelemetry)) {
      builder.enableAllFeatures();
    } else if (Boolean.TRUE.equals(
        enabledInstrumentations.getEnabled("runtime-telemetry-java17"))) {
      // default configuration
    } else {
      if (enabledInstrumentations.isEnabled("runtime-telemetry")) {
        // This only uses metrics gathered by JMX
        builder.disableAllFeatures();
      } else {
        // nothing is enabled
        return;
      }
    }

    this.closeable = RuntimeMetricsConfigUtil.configure(builder, openTelemetry).build();
  }
}
