/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.instrumentation.jmx.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.yaml.Metric;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import io.opentelemetry.instrumentation.jmx.yaml.StateMapping;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KafkaConnectRuleTest {

  @Test
  void kafkaConnectConfigParsesAndBuilds() throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();

      JmxConfig config = RuleParser.get().loadConfig(input);
      assertThat(config.getRules()).isNotEmpty();

      // ensure all metric definitions build without throwing
      for (JmxRule rule : config.getRules()) {
        assertThatCode(rule::buildMetricDef).doesNotThrowAnyException();
      }
    }
  }

  @Test
  void connectorStatusStateMappingPresent() throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      JmxConfig config = RuleParser.get().loadConfig(input);

      Optional<JmxRule> connectorRule =
          config.getRules().stream()
              .filter(
                  rule ->
                      rule.getBeans().contains("kafka.connect:type=connector-metrics,connector=*"))
              .findFirst();
      assertThat(connectorRule).isPresent();

      Metric statusMetric = connectorRule.get().getMapping().get("status");
      assertThat(statusMetric).isNotNull();

      StateMapping stateMapping = statusMetric.getStateMapping();
      assertThat(stateMapping.isEmpty()).isFalse();
      assertThat(stateMapping.getStateKeys())
          .contains(
              "running",
              "failed",
              "paused",
              "unassigned",
              "restarting",
              "degraded",
              "stopped",
              "unknown");
      assertThat(stateMapping.getDefaultStateKey()).isEqualTo("unknown");
      assertThat(stateMapping.getStateValue("RUNNING")).isEqualTo("running");
      assertThat(stateMapping.getStateValue("FAILED")).isEqualTo("failed");
      assertThat(stateMapping.getStateValue("PAUSED")).isEqualTo("paused");
      assertThat(stateMapping.getStateValue("UNKNOWN")).isEqualTo("unknown");
    }
  }
}
