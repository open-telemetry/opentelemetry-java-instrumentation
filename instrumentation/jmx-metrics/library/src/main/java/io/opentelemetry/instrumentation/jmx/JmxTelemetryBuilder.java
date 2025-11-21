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
   * Adds built-in JMX rules from classpath resource.
   *
   * @param target name of target in /jmx/rules/{target}.yaml classpath resource
   * @return builder instance
   * @throws IllegalArgumentException when classpath resource does not exist or can't be parsed
   */
  // TODO: deprecate this method after 2.23.0 release in favor of addRules
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addClassPathRules(String target) {
    String resourcePath = String.format("jmx/rules/%s.yaml", target);
    ClassLoader classLoader = JmxTelemetryBuilder.class.getClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      return addRules(inputStream, resourcePath);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Unable to load JMX rules from resource " + resourcePath, e);
    }
  }

  /**
   * Adds JMX rules from input stream
   *
   * @param input input to read rules from
   * @param description input description, used for user-friendly logs and parsing error messages
   * @throws IllegalArgumentException when input is {@literal null}
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addRules(InputStream input, String description) {
    if (input == null) {
      throw new IllegalArgumentException("JMX rules not found for " + description);
    }
    logger.log(FINE, "Adding JMX config from {0}", description);
    RuleParser parserInstance = RuleParser.get();
    parserInstance.addMetricDefsTo(metricConfiguration, input, description);
    return this;
  }

  /**
   * Adds JMX rules from file system path
   *
   * @param path path to yaml file
   * @return builder instance
   * @throws IllegalArgumentException in case of parsing errors or when file does not exist
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addRules(Path path) {
    try (InputStream inputStream = Files.newInputStream(path)) {
      return addRules(inputStream, "file " + path);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to load JMX rules from: " + path, e);
    }
  }

  /**
   * Adds custom JMX rules from file system path
   *
   * @param path path to yaml file
   * @return builder instance
   * @throws IllegalArgumentException when classpath resource does not exist or can't be parsed
   */
  // TODO: deprecate this method after 2.23.0 release in favor of addRules
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addCustomRules(Path path) {
    return addRules(path);
  }

  public JmxTelemetry build() {
    return new JmxTelemetry(openTelemetry, discoveryDelayMs, metricConfiguration);
  }
}
