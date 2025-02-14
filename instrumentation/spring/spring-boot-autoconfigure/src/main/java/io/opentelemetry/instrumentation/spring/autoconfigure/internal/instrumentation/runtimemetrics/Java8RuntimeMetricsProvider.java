/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.RuntimeMetricsConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures runtime metrics collection for Java 8.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class Java8RuntimeMetricsProvider implements RuntimeMetricsProvider {
  private static final Logger logger = LoggerFactory.getLogger(Java8RuntimeMetricsProvider.class);

  @Override
  public int minJavaVersion() {
    return 8;
  }

  @Override
  public AutoCloseable start(OpenTelemetry openTelemetry, InstrumentationConfig config) {
    logger.debug("Use runtime metrics instrumentation for Java 8");
    return RuntimeMetricsConfigUtil.configure(RuntimeMetrics.builder(openTelemetry), config);
  }
}
