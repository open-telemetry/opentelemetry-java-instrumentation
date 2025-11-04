/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class JmxTelemetryBuilder {

  private static final Logger logger = Logger.getLogger(JmxTelemetryBuilder.class.getName());

  private final OpenTelemetry openTelemetry;
  private final MetricConfiguration metricConfiguration;
  private long discoveryDelayMs;

  JmxTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.discoveryDelayMs = 0;
    this.metricConfiguration = new MetricConfiguration();
  }

  @CanIgnoreReturnValue
  public JmxTelemetryBuilder beanDiscoveryDelay(long delayMs) {
    this.discoveryDelayMs = delayMs;
    return this;
  }

  // TODO: can we use classloader as argument instead of baseClass?
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addClasspathRules(Class<?> baseClass, String targetId) {
    String yamlResource = String.format("/jmx/rules/%s.yaml", targetId);
    try (InputStream inputStream = baseClass.getResourceAsStream(yamlResource)) {
      if (inputStream != null) {
        logger.log(FINE, "Adding JMX config from classpath for {0}", yamlResource);
        RuleParser parserInstance = RuleParser.get();
        parserInstance.addMetricDefsTo(metricConfiguration, inputStream, targetId);
      } else {
        logger.log(INFO, "No support found for {0}", targetId);
      }
    } catch (Exception e) {
      logger.warning(e.getMessage());
    }
    return this;
  }

  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addCustomRules(String path) {
    logger.log(FINE, "Adding JMX config from file: {0}", path);
    RuleParser parserInstance = RuleParser.get();
    try (InputStream inputStream = Files.newInputStream(Paths.get(path))) {
      parserInstance.addMetricDefsTo(metricConfiguration, inputStream, path);
    } catch (Exception e) {
      // yaml parsing errors are caught and logged inside of addMetricDefsTo
      // only file access related exceptions are expected here
      logger.warning(e.toString());
    }
    return this;
  }

  public JmxTelemetry build() {
    return new JmxTelemetry(openTelemetry, discoveryDelayMs, metricConfiguration);
  }
}
