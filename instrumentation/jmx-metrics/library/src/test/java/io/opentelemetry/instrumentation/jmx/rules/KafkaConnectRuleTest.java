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
import org.junit.jupiter.api.Test;

class KafkaConnectRuleTest {

  @Test
  void kafkaConnectConfigParsesAndBuilds() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();
    assertThat(config.getRules()).isNotEmpty();

    // ensure all metric definitions build without throwing
    for (JmxRule rule : config.getRules()) {
      assertThatCode(rule::buildMetricDef).doesNotThrowAnyException();
    }
  }

  @Test
  void connectorStatusStateMappingPresent() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();

    JmxRule connectorRule =
        getRuleForBean(config, "kafka.connect:type=connector-metrics,connector=*");

    StateMapping stateMapping = getMetric(connectorRule, "status").getStateMapping();
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

  @Test
  void taskStatusStateMappingSuperset() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();

    JmxRule connectorTaskRule =
        getRuleForBean(
            config, "kafka.connect:type=connector-task-metrics,connector=*,task=*");

    StateMapping stateMapping = getMetric(connectorTaskRule, "status").getStateMapping();
    assertThat(stateMapping.isEmpty()).isFalse();
    assertThat(stateMapping.getStateKeys())
        .contains(
            "running",
            "failed",
            "paused",
            "unassigned",
            "restarting",
            "destroyed",
            "unknown");
    assertThat(stateMapping.getDefaultStateKey()).isEqualTo("unknown");
    assertThat(stateMapping.getStateValue("DESTROYED")).isEqualTo("destroyed");
    assertThat(stateMapping.getStateValue("RESTARTING")).isEqualTo("restarting");
    assertThat(stateMapping.getStateValue("unexpected")).isEqualTo("unknown");
  }

  @Test
  void apacheSpecificMetricsPresent() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();

    assertMappingContains(
        config, "kafka.connect:type=connect-worker-rebalance-metrics", "connect-protocol");

    assertMappingContains(
        config,
        "kafka.connect:type=connect-worker-metrics,connector=*",
        "connector-destroyed-task-count",
        "connector-failed-task-count",
        "connector-paused-task-count",
        "connector-restarting-task-count",
        "connector-running-task-count",
        "connector-total-task-count",
        "connector-unassigned-task-count");

    assertMappingContains(
        config,
        "kafka.connect:type=connector-predicate-metrics,connector=*,task=*,predicate=*",
        "predicate-class",
        "predicate-version");

    assertMappingContains(
        config,
        "kafka.connect:type=connector-transform-metrics,connector=*,task=*,transform=*",
        "transform-class",
        "transform-version");

    assertMappingContains(
        config,
        "kafka.connect:type=connector-task-metrics,connector=*,task=*",
        "connector-class",
        "connector-type",
        "connector-version",
        "header-converter-class",
        "header-converter-version",
        "key-converter-class",
        "key-converter-version",
        "task-class",
        "task-version",
        "value-converter-class",
        "value-converter-version");

    assertMappingContains(
        config,
        "kafka.connect:type=source-task-metrics,connector=*,task=*",
        "transaction-size-avg",
        "transaction-size-max",
        "transaction-size-min");

    assertMappingContains(
        config,
        "kafka.connect:type=sink-task-metrics,connector=*,task=*",
        "sink-record-lag-max");
  }

  private JmxConfig loadKafkaConnectConfig() throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();
      return RuleParser.get().loadConfig(input);
    }
  }

  private static JmxRule getRuleForBean(JmxConfig config, String bean) {
    return config.getRules().stream()
        .filter(rule -> rule.getBeans().contains(bean))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing rule for bean " + bean));
  }

  private static Metric getMetric(JmxRule rule, String metricKey) {
    Metric metric = rule.getMapping().get(metricKey);
    if (metric == null) {
      throw new AssertionError("Missing metric " + metricKey + " in rule " + rule.getBeans());
    }
    return metric;
  }

  private static void assertMappingContains(
      JmxConfig config, String bean, String... metricKeys) {
    JmxRule rule = getRuleForBean(config, bean);
    assertThat(rule.getMapping().keySet()).contains(metricKeys);
  }
}
