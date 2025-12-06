/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.yaml.Metric;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import io.opentelemetry.instrumentation.jmx.yaml.StateMapping;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaConnectRuleTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static MBeanServer mbeanServer;

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
  void confluentCompatibleMetricsCollectWithoutApacheOnlyAttributes() throws Exception {
    registerMBean(
        "kafka.connect:type=connect-worker-metrics",
        mapOf("connector-count", 1L, "task-count", 2L));

    registerMBean(
        "kafka.connect:type=connector-metrics,connector=confluent-connector",
        mapOf(
            "connector-class", "io.test.MyConnector",
            "connector-type", "source",
            "connector-version", "1.2.3",
            "status", "RUNNING"));

    registerMBean(
        "kafka.connect:type=connector-task-metrics,connector=confluent-connector,task=0",
        mapOf(
            "status", "DESTROYED",
            "connector-class", "io.test.MyConnector",
            "connector-type", "sink",
            "connector-version", "9.9",
            "task-class", "io.test.Task",
            "task-version", "1.0",
            "offset-commit-avg-time-ms", 5L));

    startKafkaConnectTelemetry();

    assertLongSum(
        "kafka.connect.worker.connector.count", Attributes.empty(), 1);
    assertLongSum("kafka.connect.worker.task.count", Attributes.empty(), 2);

    Attributes connectorStatusAttributes =
        Attributes.builder()
            .put("kafka.connect.connector", "confluent-connector")
            .put("kafka.connect.connector.state", "running")
            .put("kafka.connect.connector.class", "io.test.MyConnector")
            .put("kafka.connect.connector.type.raw", "source")
            .put("kafka.connect.connector.version", "1.2.3")
            .build();
    assertLongSum(
        "kafka.connect.connector.status", connectorStatusAttributes, 1);

    Attributes taskStatusAttributes =
        Attributes.builder()
            .put("kafka.connect.connector", "confluent-connector")
            .put("kafka.connect.task.id", "0")
            .put("kafka.connect.connector.class", "io.test.MyConnector")
            .put("kafka.connect.connector.type.raw", "sink")
            .put("kafka.connect.connector.version", "9.9")
            .put("kafka.connect.task.class", "io.test.Task")
            .put("kafka.connect.task.version", "1.0")
            .put("kafka.connect.task.state", "destroyed")
            .build();
    assertLongSum("kafka.connect.task.status", taskStatusAttributes, 1);
  }

  @Test
  void apacheSpecificMetricsAreReportedWhenPresent() throws Exception {
    registerMBean(
        "kafka.connect:type=connect-worker-rebalance-metrics",
        mapOf("connect-protocol", "eager"));

    registerMBean(
        "kafka.connect:type=connect-worker-metrics,connector=apache-connector",
        mapOf(
            "connector-running-task-count", 2L,
            "connector-unassigned-task-count", 0L));

    registerMBean(
        "kafka.connect:type=connector-predicate-metrics,connector=apache-connector,task=1,predicate=sample",
        mapOf("predicate-class", "io.test.Predicate", "predicate-version", "2.1.0"));

    registerMBean(
        "kafka.connect:type=connector-transform-metrics,connector=apache-connector,task=1,transform=mask",
        mapOf("transform-class", "io.test.Transform", "transform-version", "0.9.0"));

    registerMBean(
        "kafka.connect:type=connector-task-metrics,connector=apache-connector,task=1",
        mapOf(
            "connector-class", "io.test.ApacheConnector",
            "connector-type", "source",
            "connector-version", "3.0",
            "task-class", "io.test.Task",
            "task-version", "3.1",
            "header-converter-class", "io.test.HeaderConverter",
            "header-converter-version", "1.0",
            "key-converter-class", "io.test.KeyConverter",
            "key-converter-version", "1.1",
            "value-converter-class", "io.test.ValueConverter",
            "value-converter-version", "1.2"));

    registerMBean(
        "kafka.connect:type=source-task-metrics,connector=apache-connector,task=1",
        mapOf(
            "transaction-size-avg", 3L,
            "transaction-size-max", 6L,
            "transaction-size-min", 1L));

    registerMBean(
        "kafka.connect:type=sink-task-metrics,connector=apache-connector,task=1",
        mapOf("sink-record-lag-max", 11L));

    startKafkaConnectTelemetry();

    assertLongSum(
        "kafka.connect.worker.rebalance.protocol",
        Attributes.of(
            io.opentelemetry.api.common.AttributeKey.stringKey("kafka.connect.protocol.state"),
            "eager"),
        1);

    Attributes connectorTaskAttributes =
        Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("kafka.connect.connector"), "apache-connector");
    assertLongSum("kafka.connect.worker.connector.task.running", connectorTaskAttributes, 2);

    assertLongSum(
        "kafka.connect.predicate.class",
        Attributes.builder()
            .put("kafka.connect.connector", "apache-connector")
            .put("kafka.connect.task.id", "1")
            .put("kafka.connect.predicate", "sample")
            .put("kafka.connect.predicate.class", "io.test.Predicate")
            .put("kafka.connect.predicate.class.state", "configured")
            .put("kafka.connect.predicate.version", "2.1.0")
            .build(),
        1);

    assertLongSum(
        "kafka.connect.transform.class",
        Attributes.builder()
            .put("kafka.connect.connector", "apache-connector")
            .put("kafka.connect.task.id", "1")
            .put("kafka.connect.transform", "mask")
            .put("kafka.connect.transform.class", "io.test.Transform")
            .put("kafka.connect.transform.class.state", "configured")
            .put("kafka.connect.transform.version", "0.9.0")
            .build(),
        1);

    Attributes connectorTaskMetaAttributes =
        Attributes.builder()
            .put("kafka.connect.connector", "apache-connector")
            .put("kafka.connect.task.id", "1")
            .put("kafka.connect.connector.class", "io.test.ApacheConnector")
            .put("kafka.connect.connector.type.raw", "source")
            .put("kafka.connect.connector.version", "3.0")
            .put("kafka.connect.task.class", "io.test.Task")
            .put("kafka.connect.task.version", "3.1")
            .build();
    assertLongSum(
        "kafka.connect.task.header.converter.class",
        Attributes.builder()
            .putAll(connectorTaskMetaAttributes)
            .put("kafka.connect.converter.header.class", "io.test.HeaderConverter")
            .put("kafka.connect.task.header.converter.class.state", "configured")
            .put("kafka.connect.converter.header.version", "1.0")
            .build(),
        1);

    assertLongGauge(
        "kafka.connect.source.transaction.size.max",
        Attributes.builder()
            .put("kafka.connect.connector", "apache-connector")
            .put("kafka.connect.task.id", "1")
            .build(),
        6);

    assertLongGauge(
        "kafka.connect.sink.record.lag.max",
        Attributes.builder()
            .put("kafka.connect.connector", "apache-connector")
            .put("kafka.connect.task.id", "1")
            .build(),
        11);
  }

  @BeforeAll
  static void setUp() {
    mbeanServer = MBeanServerFactory.createMBeanServer("kafka.connect");
  }

  @AfterEach
  void cleanUp() throws Exception {
    for (ObjectName name : mbeanServer.queryNames(new ObjectName("kafka.connect:*"), null)) {
      try {
        mbeanServer.unregisterMBean(name);
      } catch (InstanceNotFoundException | MBeanRegistrationException ignored) {
        // best effort cleanup for flaky tests
      }
    }
    testing.clearData();
  }

  @AfterAll
  static void tearDown() {
    MBeanServerFactory.releaseMBeanServer(mbeanServer);
  }

  private JmxConfig loadKafkaConnectConfig() throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();
      return RuleParser.get().loadConfig(input);
    }
  }

  private static void startKafkaConnectTelemetry() {
    JmxTelemetry.builder(testing.getOpenTelemetry())
        .addClassPathRules("kafka-connect")
        .build()
        .start(() -> Collections.singletonList(mbeanServer));
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

  private static Map<String, Object> mapOf(Object... kvPairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < kvPairs.length; i += 2) {
      map.put((String) kvPairs[i], kvPairs[i + 1]);
    }
    return map;
  }

  private static void registerMBean(String objectName, Map<String, Object> attributes)
      throws Exception {
    mbeanServer.registerMBean(new MapBackedDynamicMBean(attributes), new ObjectName(objectName));
  }

  private static void assertLongSum(String metricName, Attributes attributes, long expectedValue) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metricName,
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  boolean matched =
                      metric.getLongSumData().getPoints().stream()
                          .anyMatch(
                              pointData ->
                                  attributesMatch(pointData.getAttributes(), attributes)
                                      && pointData.getValue() == expectedValue);
                  assertThat(matched)
                      .as(
                          "Expected %s to have a point with attributes %s and value %s",
                          metricName, attributes, expectedValue)
                      .isTrue();
                }));
  }

  private static void assertLongGauge(String metricName, Attributes attributes, long expected) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metricName,
        metrics ->
            metrics.anySatisfy(
              metric -> {
                  boolean matched =
                      metric.getLongGaugeData().getPoints().stream()
                          .anyMatch(
                              pointData ->
                                  attributesMatch(pointData.getAttributes(), attributes)
                                      && pointData.getValue() == expected);
                  assertThat(matched)
                      .as(
                          "Expected %s to have a point with attributes %s and value %s",
                          metricName, attributes, expected)
                      .isTrue();
                }));
  }

  private static boolean attributesMatch(Attributes actual, Attributes expected) {
    for (Map.Entry<io.opentelemetry.api.common.AttributeKey<?>, Object> entry :
        expected.asMap().entrySet()) {
      Object actualValue = actual.get(entry.getKey());
      if (!entry.getValue().equals(actualValue)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Minimal DynamicMBean implementation backed by a simple attribute map. This keeps the functional
   * Kafka Connect coverage self contained in the test file.
   */
  static class MapBackedDynamicMBean extends NotificationBroadcasterSupport implements DynamicMBean {

    private final Map<String, Object> attributes;
    private long sequenceNumber = 1;

    MapBackedDynamicMBean(Map<String, Object> attributes) {
      this.attributes = new HashMap<>(attributes);
    }

    @Override
    public Object getAttribute(String attribute)
        throws AttributeNotFoundException, MBeanException, ReflectionException {
      if (!attributes.containsKey(attribute)) {
        throw new AttributeNotFoundException(attribute);
      }
      return attributes.get(attribute);
    }

    @Override
    public void setAttribute(Attribute attribute) {
      attributes.put(attribute.getName(), attribute.getValue());
      Notification n =
          new Notification(
              "jmx.attribute.changed",
              this,
              sequenceNumber++,
              System.currentTimeMillis(),
              attribute.getName());
      sendNotification(n);
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
      AttributeList list = new AttributeList();
      for (String attr : attributes) {
        Object value = this.attributes.get(attr);
        if (value != null) {
          list.add(new Attribute(attr, value));
        }
      }
      return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
      AttributeList updated = new AttributeList();
      for (Object attribute : attributes) {
        if (attribute instanceof Attribute) {
          Attribute attr = (Attribute) attribute;
          setAttribute(attr);
          updated.add(attr);
        }
      }
      return updated;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
        throws MBeanException, ReflectionException {
      return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
      List<javax.management.MBeanAttributeInfo> infos = new ArrayList<>();
      attributes.forEach(
          (name, value) ->
              infos.add(
                  new javax.management.MBeanAttributeInfo(
                      name,
                      value.getClass().getName(),
                      name + " attribute",
                      true,
                      true,
                      false)));
      return new MBeanInfo(
          this.getClass().getName(),
          "Map backed test MBean",
          infos.toArray(new javax.management.MBeanAttributeInfo[0]),
          null,
          null,
          null);
    }
  }
}
