/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

import static java.util.Arrays.asList;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricDef;
import io.opentelemetry.instrumentation.jmx.internal.handler.HandlerRegistry;
import io.opentelemetry.instrumentation.jmx.internal.yaml.RuleParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InternalMetricsDefinitions {

  private static final Logger logger = Logger.getLogger(InternalMetricsDefinitions.class.getName());

  private static final Set<String> SUPPORTED_SYSTEMS =
      Collections.unmodifiableSet(
          new HashSet<>(
              asList(
                  "activemq",
                  "camel",
                  "cassandra",
                  "kafka-broker",
                  "kafka-connect",
                  "hadoop",
                  "jetty",
                  "jvm",
                  "tomcat",
                  "wildfly")));

  private final ClassLoader classLoader;
  private final List<RuleSet> loadedRules = new ArrayList<>();

  public InternalMetricsDefinitions(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  // intentionally not static for easier testing
  public Set<String> getSupportedSystems() {
    return SUPPORTED_SYSTEMS;
  }

  /**
   * Load internal rules for supported systems, filtered by the provided filter
   *
   * @param systemFilter filter on supported systems identifiers.
   */
  public void loadInternalRules(IncludeExclude systemFilter, HandlerRegistry handlerRegistry) {
    getSupportedSystems().stream()
        .filter(systemFilter::matches)
        .forEach(
            s -> {
              loadRules(s, false, handlerRegistry);
              loadRules(s, true, handlerRegistry);
            });
  }

  private void loadRules(String systemName, boolean stable, HandlerRegistry handlerRegistry) {
    String path = getRulesPath(systemName, stable);
    if (path == null) {
      // silently ignore non-existing rules
      return;
    }

    try (InputStream input = classLoader.getResourceAsStream(path)) {
      logger.log(FINE, "loading embedded JMX rules from {0}", path);
      List<MetricDef> metricDefs = RuleParser.get().parseMetricDefs(input);
      loadedRules.add(new RuleSet(metricDefs, stable, handlerRegistry));
    } catch (IOException e) {
      throw new IllegalStateException("unable to load resource " + path, e);
    }
  }

  /**
   * Get metric names for a given stability.
   *
   * @param stable true to get stable metrics, false to get unstable metrics
   * @return set of metrics for the provided stability, may be empty.
   */
  public Set<String> getMetricNames(boolean stable) {
    Set<String> result = new HashSet<>();
    for (RuleSet ruleSet : loadedRules) {
      if (ruleSet.stable == stable) {
        result.addAll(ruleSet.getMetricNames());
      }
    }
    return result;
  }

  /**
   * Get all internal metrics definitions.
   *
   * @return list of all internal metrics definitions, may be empty.
   */
  public List<MetricDef> getAllMetricDefs() {
    List<MetricDef> result = new ArrayList<>();
    for (RuleSet ruleSet : loadedRules) {
      result.addAll(ruleSet.metricDefs);
    }
    return result;
  }

  /**
   * Get path to existing rules resources.
   *
   * @param system system name
   * @param stable true to load stable rules, false for non-stable rules
   * @return path to rules resource, {@literal null} if there is none
   */
  @Nullable
  public String getRulesPath(String system, boolean stable) {
    String path = String.format("jmx/rules/%s.yaml", system);
    if (!stable) {
      path = String.format("jmx/rules/%s_unstable.yaml", system);
    }

    // Probe existence by attempting to read it using same method as actually reading it.
    try (InputStream input = classLoader.getResourceAsStream(path)) {
      return input != null ? path : null;
    } catch (IOException e) {
      // no io exception expected when loading resources
      throw new IllegalStateException(e);
    }
  }

  private static class RuleSet {
    private final boolean stable;
    private final List<MetricDef> metricDefs;
    private final Set<String> metricNames = new HashSet<>();

    RuleSet(List<MetricDef> metricDefs, boolean stable, HandlerRegistry handlerRegistry) {
      this.metricDefs = metricDefs;
      this.stable = stable;
      for (MetricDef def : metricDefs) {
        metricNames.addAll(def.getMetricNames());
        for (String handlerName : def.getHandlerNames()) {
          ExperimentalJmxMetricHandler handler = handlerRegistry.getHandler(handlerName);
          if (handler == null) {
            throw new IllegalArgumentException(
                "Unable to resolve handler " + handlerName + " with provided registry");
          }
          metricNames.addAll(handler.getMetricNames());
        }
      }
    }

    Set<String> getMetricNames() {
      return metricNames;
    }
  }
}
