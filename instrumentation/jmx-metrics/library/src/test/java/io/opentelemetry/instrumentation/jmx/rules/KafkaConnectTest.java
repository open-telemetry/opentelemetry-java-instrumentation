/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
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

class KafkaConnectTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static MBeanServer mbeanServer;

  @Test
  void kafkaConnectConfigParsesAndBuilds() throws Exception {
    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig config = loadKafkaConnectConfig();
    assertThat(config.getRules()).isNotEmpty();

    // ensure all metric definitions build without throwing
    for (io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule rule : config.getRules()) {
      assertThatCode(rule::buildMetricDef).doesNotThrowAnyException();
    }
  }

  @Test
  void kafkaConnectRulesUseBasicMetricTypes() throws Exception {
    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig config = loadKafkaConnectConfig();

    assertThat(config.getRules())
        .allSatisfy(
            rule -> {
              assertThat(rule.getMetricType())
                  .isNotEqualTo(
                      io.opentelemetry.instrumentation.jmx.internal.engine.MetricInfo.Type.STATE);
              rule.getMapping()
                  .values()
                  .forEach(
                      metric ->
                          assertThat(metric.getMetricType())
                              .isNotEqualTo(
                                  io.opentelemetry.instrumentation.jmx.internal.engine.MetricInfo
                                      .Type.STATE));
            });
  }

  @Test
  void statusStateMappingsPresent() throws Exception {
    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig config = loadKafkaConnectConfig();

    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule connectorRule =
        getRuleForBean(config, "kafka.connect:type=connector-metrics,connector=*");

    io.opentelemetry.instrumentation.jmx.internal.yaml.StateMapping connectorStateMapping =
        getMetric(connectorRule, "status").getStateMapping();
    assertThat(getMetric(connectorRule, "status").getMetricType())
        .isEqualTo(
            io.opentelemetry.instrumentation.jmx.internal.engine.MetricInfo.Type.UPDOWNCOUNTER);
    assertThat(connectorStateMapping.isEmpty()).isFalse();
    assertThat(connectorStateMapping.getStateKeys())
        .contains(
            "running",
            "failed",
            "paused",
            "unassigned",
            "restarting",
            "degraded",
            "stopped",
            "unknown");
    assertThat(connectorStateMapping.getDefaultStateKey()).isEqualTo("unknown");
    assertThat(connectorStateMapping.getStateValue("RUNNING")).isEqualTo("running");
    assertThat(connectorStateMapping.getStateValue("FAILED")).isEqualTo("failed");
    assertThat(connectorStateMapping.getStateValue("PAUSED")).isEqualTo("paused");
    assertThat(connectorStateMapping.getStateValue("UNKNOWN")).isEqualTo("unknown");

    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule connectorTaskRule =
        getRuleForBean(config, "kafka.connect:type=connector-task-metrics,connector=*,task=*");

    io.opentelemetry.instrumentation.jmx.internal.yaml.StateMapping taskStateMapping =
        getMetric(connectorTaskRule, "status").getStateMapping();
    assertThat(getMetric(connectorTaskRule, "status").getMetricType())
        .isEqualTo(
            io.opentelemetry.instrumentation.jmx.internal.engine.MetricInfo.Type.UPDOWNCOUNTER);
    assertThat(taskStateMapping.isEmpty()).isFalse();
    assertThat(taskStateMapping.getStateKeys())
        .contains(
            "running", "failed", "paused", "unassigned", "restarting", "destroyed", "unknown");
    assertThat(taskStateMapping.getDefaultStateKey()).isEqualTo("unknown");
    assertThat(taskStateMapping.getStateValue("DESTROYED")).isEqualTo("destroyed");
    assertThat(taskStateMapping.getStateValue("RESTARTING")).isEqualTo("restarting");
    assertThat(taskStateMapping.getStateValue("unexpected")).isEqualTo("unknown");
  }

  @Test
  void metricsAreReportedAcrossVariants() throws Exception {
    String confluentConnector = "confluent-connector";
    String confluentTaskId = "0";
    String apacheConnector = "apache-connector";
    String apacheTaskId = "1";
    String errorConnector = "error-connector";
    String errorTaskId = "1";
    String errorNegativeTaskId = "2";

    registerMBean(
        connectWorkerMetricsBean(),
        mapOf(
            "connector-count",
            1L,
            "task-count",
            2L,
            "connector-startup-failure-total",
            1L,
            "connector-startup-success-total",
            3L,
            "task-startup-failure-total",
            2L,
            "task-startup-success-total",
            4L));

    registerMBean(connectorMetricsBean(confluentConnector), mapOf("status", "RUNNING"));

    registerMBean(
        connectorTaskMetricsBean(confluentConnector, confluentTaskId),
        mapOf(
            "status",
            "DESTROYED",
            "offset-commit-avg-time-ms",
            1500L,
            "offset-commit-max-time-ms",
            2500L));

    registerMBean(
        connectWorkerRebalanceMetricsBean(),
        mapOf(
            "connect-protocol",
            "eager",
            "rebalance-avg-time-ms",
            1500L,
            "rebalance-max-time-ms",
            2500L,
            "time-since-last-rebalance-ms",
            3000L,
            "rebalancing",
            "true"));

    registerMBean(
        connectWorkerMetricsBeanForConnector(apacheConnector),
        mapOf(
            "connector-running-task-count", 2L,
            "connector-failed-task-count", 1L,
            "connector-paused-task-count", 1L));

    registerMBean(
        sourceTaskMetricsBean(apacheConnector, apacheTaskId),
        mapOf("poll-batch-avg-time-ms", 500L, "transaction-size-max", 6L));

    registerMBean(
        sinkTaskMetricsBean(apacheConnector, apacheTaskId),
        mapOf("put-batch-avg-time-ms", 1200L, "sink-record-lag-max", 11L));

    registerMBean(
        taskErrorMetricsBean(errorConnector, errorTaskId),
        mapOf(
            "deadletterqueue-produce-failures",
            2L,
            "last-error-timestamp",
            2000L,
            "total-errors-logged",
            3L));

    registerMBean(
        taskErrorMetricsBean(errorConnector, errorNegativeTaskId),
        mapOf("last-error-timestamp", -1L));

    startKafkaConnectTelemetry();

    assertLongSum("kafka.connect.worker.connector.count", Attributes.empty(), 1);
    assertLongSum("kafka.connect.worker.task.count", Attributes.empty(), 2);
    assertLongSum(
        "kafka.connect.worker.connector.startup", connectorStartupResultAttributes("failure"), 1);
    assertLongSum(
        "kafka.connect.worker.connector.startup", connectorStartupResultAttributes("success"), 3);
    assertLongSum("kafka.connect.worker.task.startup", taskStartupResultAttributes("failure"), 2);
    assertLongSum("kafka.connect.worker.task.startup", taskStartupResultAttributes("success"), 4);

    assertLongSum(
        "kafka.connect.connector.status",
        connectorStatusAttributes(confluentConnector, "running"),
        1);
    assertLongSum(
        "kafka.connect.task.status",
        taskStatusAttributes(confluentConnector, confluentTaskId, "destroyed"),
        1);

    Attributes confluentTaskAttributes = taskAttributes(confluentConnector, confluentTaskId);
    assertDoubleGauge("kafka.connect.task.offset.commit.avg.time", confluentTaskAttributes, 1.5d);
    assertDoubleGauge("kafka.connect.task.offset.commit.max.time", confluentTaskAttributes, 2.5d);

    assertLongSum(
        "kafka.connect.worker.rebalance.protocol",
        Attributes.of(
            io.opentelemetry.api.common.AttributeKey.stringKey("kafka.connect.protocol.state"),
            "eager"),
        1);
    assertDoubleGauge("kafka.connect.worker.rebalance.avg.time", Attributes.empty(), 1.5d);
    assertDoubleGauge("kafka.connect.worker.rebalance.max.time", Attributes.empty(), 2.5d);
    assertDoubleGauge("kafka.connect.worker.rebalance.since_last", Attributes.empty(), 3.0d);
    assertLongSum(
        "kafka.connect.worker.rebalance.active", rebalanceStateAttributes("rebalancing"), 1);

    assertLongSum(
        "kafka.connect.worker.connector.task.count",
        connectorTaskStateAttributes(apacheConnector, "running"),
        2);
    assertLongSum(
        "kafka.connect.worker.connector.task.count",
        connectorTaskStateAttributes(apacheConnector, "failed"),
        1);
    assertLongSum(
        "kafka.connect.worker.connector.task.count",
        connectorTaskStateAttributes(apacheConnector, "paused"),
        1);

    Attributes apacheTaskAttributes = taskAttributes(apacheConnector, apacheTaskId);
    assertDoubleGauge("kafka.connect.source.poll.batch.avg.time", apacheTaskAttributes, 0.5d);
    assertDoubleGauge("kafka.connect.sink.put.batch.avg.time", apacheTaskAttributes, 1.2d);
    assertLongGauge("kafka.connect.source.transaction.size.max", apacheTaskAttributes, 6);
    assertLongGauge("kafka.connect.sink.record.lag.max", apacheTaskAttributes, 11);

    Attributes task1Attributes = taskAttributes(errorConnector, errorTaskId);
    Attributes task2Attributes = taskAttributes(errorConnector, errorNegativeTaskId);
    assertLongSum("kafka.connect.task.error.deadletterqueue.produce.failures", task1Attributes, 2);
    assertLongSum("kafka.connect.task.error.total.errors.logged", task1Attributes, 3);
    assertDoubleGauge("kafka.connect.task.error.last.error.timestamp", task1Attributes, 2.0d);
    assertNoDoubleGaugePoint("kafka.connect.task.error.last.error.timestamp", task2Attributes);
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

  private io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig loadKafkaConnectConfig()
      throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();
      return io.opentelemetry.instrumentation.jmx.internal.yaml.RuleParser.get().loadConfig(input);
    }
  }

  private static void startKafkaConnectTelemetry() throws Exception {
    try (InputStream input =
        KafkaConnectTest.class
            .getClassLoader()
            .getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();
      JmxTelemetry.builder(testing.getOpenTelemetry())
          .addRules(input)
          .build()
          .start(() -> Collections.singletonList(mbeanServer));
    }
  }

  private static io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule getRuleForBean(
      io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig config, String bean) {
    return config.getRules().stream()
        .filter(rule -> rule.getBeans().contains(bean))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing rule for bean " + bean));
  }

  private static io.opentelemetry.instrumentation.jmx.internal.yaml.Metric getMetric(
      io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule rule, String metricKey) {
    io.opentelemetry.instrumentation.jmx.internal.yaml.Metric metric =
        rule.getMapping().get(metricKey);
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

  private static String connectWorkerMetricsBean() {
    return "kafka.connect:type=connect-worker-metrics";
  }

  private static String connectWorkerMetricsBeanForConnector(String connector) {
    return "kafka.connect:type=connect-worker-metrics,connector=" + connector;
  }

  private static String connectWorkerRebalanceMetricsBean() {
    return "kafka.connect:type=connect-worker-rebalance-metrics";
  }

  private static String connectorMetricsBean(String connector) {
    return "kafka.connect:type=connector-metrics,connector=" + connector;
  }

  private static String connectorTaskMetricsBean(String connector, String taskId) {
    return "kafka.connect:type=connector-task-metrics,connector=" + connector + ",task=" + taskId;
  }

  private static String sourceTaskMetricsBean(String connector, String taskId) {
    return "kafka.connect:type=source-task-metrics,connector=" + connector + ",task=" + taskId;
  }

  private static String sinkTaskMetricsBean(String connector, String taskId) {
    return "kafka.connect:type=sink-task-metrics,connector=" + connector + ",task=" + taskId;
  }

  private static String taskErrorMetricsBean(String connector, String taskId) {
    return "kafka.connect:type=task-error-metrics,connector=" + connector + ",task=" + taskId;
  }

  private static Attributes connectorStatusAttributes(String connector, String state) {
    return Attributes.builder()
        .put("kafka.connect.connector", connector)
        .put("kafka.connect.connector.state", state)
        .build();
  }

  private static Attributes taskStatusAttributes(String connector, String taskId, String state) {
    return Attributes.builder()
        .put("kafka.connect.connector", connector)
        .put("kafka.connect.task.id", taskId)
        .put("kafka.connect.task.state", state)
        .build();
  }

  private static Attributes taskAttributes(String connector, String taskId) {
    return Attributes.builder()
        .put("kafka.connect.connector", connector)
        .put("kafka.connect.task.id", taskId)
        .build();
  }

  private static Attributes connectorTaskStateAttributes(String connector, String state) {
    return Attributes.builder()
        .put("kafka.connect.connector", connector)
        .put("kafka.connect.worker.connector.task.state", state)
        .build();
  }

  private static Attributes connectorStartupResultAttributes(String result) {
    return Attributes.builder()
        .put("kafka.connect.worker.connector.startup.result", result)
        .build();
  }

  private static Attributes taskStartupResultAttributes(String result) {
    return Attributes.builder().put("kafka.connect.worker.task.startup.result", result).build();
  }

  private static Attributes rebalanceStateAttributes(String state) {
    return Attributes.builder().put("kafka.connect.worker.rebalance.state", state).build();
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

  private static void assertDoubleGauge(String metricName, Attributes attributes, double expected) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metricName,
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  boolean matched =
                      metric.getDoubleGaugeData().getPoints().stream()
                          .anyMatch(
                              pointData ->
                                  attributesMatch(pointData.getAttributes(), attributes)
                                      && Math.abs(pointData.getValue() - expected) < 1e-6);
                  assertThat(matched)
                      .as(
                          "Expected %s to have a point with attributes %s and value %s",
                          metricName, attributes, expected)
                      .isTrue();
                }));
  }

  private static void assertNoDoubleGaugePoint(String metricName, Attributes attributes) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metricName,
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  boolean matched =
                      metric.getDoubleGaugeData().getPoints().stream()
                          .anyMatch(
                              pointData -> attributesMatch(pointData.getAttributes(), attributes));
                  assertThat(matched)
                      .as("Expected %s to have no point with attributes %s", metricName, attributes)
                      .isFalse();
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
  static class MapBackedDynamicMBean extends NotificationBroadcasterSupport
      implements DynamicMBean {

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
                      name, value.getClass().getName(), name + " attribute", true, true, false)));
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
