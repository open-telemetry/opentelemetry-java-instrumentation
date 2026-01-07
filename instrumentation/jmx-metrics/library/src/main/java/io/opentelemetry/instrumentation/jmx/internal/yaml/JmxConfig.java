/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.yaml;

import io.opentelemetry.instrumentation.jmx.internal.engine.MetricConfiguration;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricDef;
import java.util.List;

/**
 * JMX configuration as a set of JMX rules. Objects of this class are created and populated by the
 * YAML parser.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JmxConfig {

  // Used by the YAML parser
  //   rules:
  //     - JMX_DEFINITION1
  //     - JMX_DEFINITION2
  private final List<JmxRule> rules;

  public JmxConfig(List<JmxRule> rules) {
    this.rules = rules;
  }

  public List<JmxRule> getRules() {
    return rules;
  }

  /**
   * Converts the rules from this object into MetricDefs and adds them to the specified
   * MetricConfiguration.
   *
   * @param configuration MetricConfiguration to add MetricDefs to
   * @throws Exception an exception if the rule conversion cannot be performed
   */
  void addMetricDefsTo(MetricConfiguration configuration) throws Exception {
    for (JmxRule rule : rules) {
      MetricDef metricDef = rule.buildMetricDef();
      configuration.addMetricDef(metricDef);
    }
  }
}
