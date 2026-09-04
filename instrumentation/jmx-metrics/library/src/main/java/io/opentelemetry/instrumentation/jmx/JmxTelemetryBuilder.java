/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler;
import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricConfiguration;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricDef;
import io.opentelemetry.instrumentation.jmx.internal.handler.HandlerRegistry;
import io.opentelemetry.instrumentation.jmx.internal.yaml.RuleParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Builder for {@link JmxTelemetry} */
public final class JmxTelemetryBuilder {

  private static final Logger logger = Logger.getLogger(JmxTelemetryBuilder.class.getName());

  private final OpenTelemetry openTelemetry;
  private final MetricConfiguration metricConfiguration;
  private long discoveryDelayMs;
  private ClassLoader classLoader = JmxTelemetryBuilder.class.getClassLoader();
  private ComponentLoader componentLoader = ComponentLoader.forClassLoader(classLoader);
  private final Set<String> registeredMetrics = new HashSet<>();
  private final Set<String> registeredHandlers = new HashSet<>();
  private IncludeExclude metrics = IncludeExclude.builder().build();

  @Nullable private IncludeExclude stableMetricsSystemFilter = null;
  @Nullable private IncludeExclude unstableMetricsSystemFilter = null;
  // include all systems by default
  private IncludeExclude internalMetricsSystemFilter = IncludeExclude.builder().build();
  // exclude all unstable metrics by default
  private IncludeExclude internalMetricsUnstableMetricsFilter =
      IncludeExclude.builder().setExcluded("*").build();

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
   * Adds JMX rules from input stream, all metrics are included unless filtered out by the {@link
   * #setMetrics(IncludeExclude)} method.
   *
   * @param input input to read rules from
   * @throws IllegalArgumentException when input is {@literal null} or can't be parsed
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addRules(InputStream input) {
    if (input == null) {
      throw new IllegalArgumentException("missing JMX rules");
    }
    List<MetricDef> metricDefs = RuleParser.get().parseMetricDefs(input);

    for (MetricDef metricDef : metricDefs) {
      metricConfiguration.addMetricDef(metricDef);
      registeredMetrics.addAll(metricDef.getMetricNames());
      registeredHandlers.addAll(metricDef.getHandlerNames());
    }
    return this;
  }

  /**
   * Adds JMX rules from file system path, all metrics are included unless filtered out by the
   * {@link #setMetrics(IncludeExclude)} method.
   *
   * @param path path to yaml file
   * @return builder instance
   * @throws IllegalArgumentException in case of parsing errors or when file does not exist
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder addRules(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("missing JMX rules");
    }
    try (InputStream inputStream = Files.newInputStream(path)) {
      logger.log(FINE, "Adding JMX config from file {0}", path);
      return addRules(inputStream);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to load JMX rules from: " + path, e);
    }
  }

  /**
   * Set metrics to include and exclude
   *
   * @param metrics metrics to include/exclude
   * @return this
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder setMetrics(IncludeExclude metrics) {
    this.metrics = metrics;
    return this;
  }

  /**
   * Configure loading embedded stable metrics definitions for the specified systems.
   *
   * @param systemFilter system name filter, use {@code IncludeExclude.builder().build()} to include
   *     all.
   * @return this
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder loadStableMetrics(IncludeExclude systemFilter) {
    this.stableMetricsSystemFilter = systemFilter;
    return this;
  }

  /**
   * Configure loading embedded unstable metrics definitions for the specified systems.
   *
   * @param systemFilter system name filter, use {@code IncludeExclude.builder().build()} to include
   *     all.
   * @return this
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder loadUnstableMetrics(IncludeExclude systemFilter) {
    this.unstableMetricsSystemFilter = systemFilter;
    return this;
  }

  @CanIgnoreReturnValue
  public JmxTelemetryBuilder internalMetricsSystemFilter(IncludeExclude systemFilter) {
    internalMetricsSystemFilter = systemFilter;
    return this;
  }

  @CanIgnoreReturnValue
  public JmxTelemetryBuilder internalMetricsUnstableMetricsFilter(IncludeExclude metricsFilter) {
    internalMetricsUnstableMetricsFilter = metricsFilter;
    return this;
  }

  /**
   * Sets the {@link ClassLoader} to be used to load SPI implementations and internal resource
   * loading
   */
  @CanIgnoreReturnValue
  public JmxTelemetryBuilder setServiceClassLoader(ClassLoader serviceClassLoader) {
    requireNonNull(serviceClassLoader, "serviceClassLoader");
    this.componentLoader = ComponentLoader.forClassLoader(serviceClassLoader);
    this.classLoader = serviceClassLoader;
    return this;
  }

  // package private for testing
  JmxTelemetry build(InternalMetricsDefinitions metricsDefinitions) {
    HandlerRegistry handlerRegistry = new HandlerRegistry();
    handlerRegistry.load(componentLoader);

    // metric names from handlers are only available after handlers have been resolved
    // also, we should only include handlers that have been explicitly registered in the rules.
    registeredHandlers.forEach(
        h -> {
          ExperimentalJmxMetricHandler handler = handlerRegistry.getHandler(h);
          if (handler != null) {
            registeredMetrics.addAll(handler.getMetricNames());
          }
        });

    // filter on system name let the caller control which systems are supported.
    metricsDefinitions.loadInternalRules(internalMetricsSystemFilter, handlerRegistry);
    // all metric defs are loaded and configured, even if metrics may be filtered-out later
    metricsDefinitions.getAllMetricDefs().forEach(metricConfiguration::addMetricDef);

    Set<String> stableMetrics = metricsDefinitions.getMetricNames(true);
    Set<String> unstableMetrics = metricsDefinitions.getMetricNames(false);

    // make all internal metrics as registered, but they can be filtered out
    registeredMetrics.addAll(stableMetrics);
    registeredMetrics.addAll(unstableMetrics);

    System.out.println("user provided metrics filter: " + metrics);
    System.out.println("unstable metrics filter " + internalMetricsUnstableMetricsFilter);

    // make the metric filter ignore the non-stable metrics not explicitly in the opt-in filter
    Set<String> excludePatterns = new HashSet<>(metrics.getExcluded());
    unstableMetrics.stream()
        .filter(m -> !internalMetricsUnstableMetricsFilter.matches(m))
        .filter(m -> metrics.matches(m)) // no need to exclude it if already excluded
        .forEach(excludePatterns::add);

    IncludeExclude effectiveMetricsFilter =
        IncludeExclude.builder()
            .setIncluded(metrics.getIncluded())
            .setExcluded(excludePatterns)
            .build();

    if (logger.isLoggable(INFO)) {
      // making it easier to debug include/exclude patterns
      registeredMetrics.forEach(
          m ->
              System.out.printf(
                  "JMX metric '%s' %s by configuration%n",
                  m, effectiveMetricsFilter.matches(m) ? "included" : "excluded"));
    }

    return new JmxTelemetry(
        openTelemetry,
        discoveryDelayMs,
        metricConfiguration,
        handlerRegistry,
        effectiveMetricsFilter);
  }

  public JmxTelemetry build() {
    return build(new InternalMetricsDefinitions(classLoader));
  }

  // package-private for testing
  Set<String> getRegisteredMetrics() {
    return Collections.unmodifiableSet(registeredMetrics);
  }

  // package-private for testing
  Set<String> getInternalRulesToLoad(InternalMetricsDefinitions internalMetrics) {
    Set<String> rulesToLoad = new HashSet<>();
    if (stableMetricsSystemFilter != null || unstableMetricsSystemFilter != null) {
      internalMetrics
          .getSupportedSystems()
          .forEach(
              system -> {
                boolean includeStable =
                    stableMetricsSystemFilter != null && stableMetricsSystemFilter.matches(system);
                boolean includeUnstable =
                    unstableMetricsSystemFilter != null
                        && unstableMetricsSystemFilter.matches(system);
                rulesToLoad.addAll(
                    internalMetrics.getRulesForSystem(system, includeStable, includeUnstable));
              });
    }
    return rulesToLoad;
  }
}
