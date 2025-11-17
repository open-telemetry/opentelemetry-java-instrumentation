/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static java.util.logging.Level.FINE;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Logger;

/** Builder for {@link JmxTelemetry} */
public final class JmxTelemetryBuilder {

  private static final Logger logger = Logger.getLogger(JmxTelemetryBuilder.class.getName());

  private final OpenTelemetry openTelemetry;
  private final MetricConfiguration metricConfiguration;
  private long discoveryDelayMs;

  JmxTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.discoveryDelayMs = 0;
    this.metricConfiguration = new MetricConfiguration();
  }

  /**
   * Sets initial delay for MBean discovery
   *
   * @param delay delay
   * @return builder instance
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder beanDiscoveryDelay(Duration delay) {
    if (delay.isNegative()) {
      throw new IllegalArgumentException("delay must be positive or zero");
    }
    this.discoveryDelayMs = delay.toMillis();
    return this;
  }

  /**
   * Adds built-in JMX rules from classpath resource
   *
   * @param target name of target in /jmx/rules/{target}.yaml classpath resource
   * @return builder instance
   * @throws IllegalArgumentException when classpath resource does not exist or can't be parsed
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addClassPathRules(String target) {
    String yamlResource = String.format("jmx/rules/%s.yaml", target);
    try (InputStream inputStream =
        JmxTelemetryBuilder.class.getClassLoader().getResourceAsStream(yamlResource)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("JMX rules not found in classpath: " + yamlResource);
      }
      logger.log(FINE, "Adding JMX config from classpath for {0}", yamlResource);
      RuleParser parserInstance = RuleParser.get();
      parserInstance.addMetricDefsTo(metricConfiguration, inputStream, target);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Unable to load JMX rules from classpath: " + yamlResource, e);
    }
    return this;
  }

  /**
   * Adds custom JMX rules from file system path
   *
   * @param path path to yaml file
   * @return builder instance
   * @throws IllegalArgumentException when classpath resource does not exist or can't be parsed
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addCustomRules(Path path) {
    logger.log(FINE, "Adding JMX config from file: {0}", path);
    RuleParser parserInstance = RuleParser.get();
    try (InputStream inputStream = Files.newInputStream(path)) {
      parserInstance.addMetricDefsTo(metricConfiguration, inputStream, path.toString());
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to load JMX rules in path: " + path, e);
    }
    return this;
  }

  public JmxTelemetry build() {
    return new JmxTelemetry(openTelemetry, discoveryDelayMs, metricConfiguration);
  }
}
