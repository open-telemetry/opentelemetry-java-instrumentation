/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.system.JavaVersion;

/**
 * Configures runtime metrics collection for Java 8.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnJava(value = JavaVersion.SEVENTEEN, range = ConditionalOnJava.Range.OLDER_THAN)
public class Java8RuntimeMetricsAutoConfiguration implements RuntimeMetricsProvider {
  private static final Logger logger = LoggerFactory.getLogger(Java8RuntimeMetricsAutoConfiguration.class);

  @Override
  public void start(
      OpenTelemetry openTelemetry, Consumer<Runnable> shutdownHook, InstrumentationConfig config) {
    logger.debug("Use runtime metrics instrumentation for Java 8");
    RuntimeMetrics.builder(openTelemetry)
        .setShutdownHook(shutdownHook)
        .startFromInstrumentationConfig(config);
  }

  public class Java8MetricsCondition extends AnyNestedCondition {
    public Java8MetricsCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnJava(value = JavaVersion.SEVENTEEN, range = ConditionalOnJava.Range.OLDER_THAN)
    class OlderThanJava17 {}

    // GraalVM native image does not support Java 17 metrics yet
    @ConditionalOnProperty("org.graalvm.nativeimage.imagecode")
    class GraalVM {}
  }
}
