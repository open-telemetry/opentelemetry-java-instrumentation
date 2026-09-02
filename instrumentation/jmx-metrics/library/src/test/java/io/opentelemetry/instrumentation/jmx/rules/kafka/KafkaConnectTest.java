/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricInfo;
import io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.internal.yaml.Metric;
import io.opentelemetry.instrumentation.jmx.internal.yaml.RuleParser;
import io.opentelemetry.instrumentation.jmx.internal.yaml.StateMapping;
import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

class KafkaConnectTest extends TargetSystemTest {
  private static final String KAFKA_IMAGE = "apache/kafka:3.8.0";
  private static final String SOURCE_CONNECTOR = "file-source";
  private static final String SINK_CONNECTOR = "file-sink";
  private static final String SOURCE_FILE_PATH = "/tmp/source.txt";
  private static final String SINK_FILE_PATH = "/tmp/sink.txt";
  private static final String TOPIC = "connect-test-topic";
  private static final String DLQ_TOPIC = "connect-dead-letter";
  private static final Set<String> OPTIONAL_APACHE_METRICS = new HashSet<>();

  private static final WebClient client = WebClient.of();

  static {
    // Apache Kafka Connect 3.8 file connectors do not expose these metrics.
    Collections.addAll(
        OPTIONAL_APACHE_METRICS,
        "kafka.connect.sink.record.lag.max",
        "kafka.connect.task.offset.commit.time.average",
        "kafka.connect.task.offset.commit.time.max",
        "kafka.connect.source.transaction.size.average",
        "kafka.connect.source.transaction.size.max",
        "kafka.connect.source.transaction.size.min");
  }

  @Test
  void kafkaConnectRulesUseBasicMetricTypes() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();

    assertThat(config.getRules())
        .allSatisfy(
            rule -> {
              assertThat(rule.getMetricType()).isNotEqualTo(MetricInfo.Type.STATE);
              rule.getMapping()
                  .values()
                  .forEach(
                      metric ->
                          assertThat(metric.getMetricType()).isNotEqualTo(MetricInfo.Type.STATE));
            });
  }

  @Test
  void statusStateMappingsPresent() throws Exception {
    JmxConfig config = loadKafkaConnectConfig();

    JmxRule connectorRule =
        getRuleForBean(config, "kafka.connect:type=connector-metrics,connector=*");

    StateMapping connectorStateMapping = getMetric(connectorRule, "status").getStateMapping();
    assertThat(getMetric(connectorRule, "status").getMetricType())
        .isEqualTo(MetricInfo.Type.UPDOWNCOUNTER);
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

    JmxRule connectorTaskRule =
        getRuleForBean(config, "kafka.connect:type=connector-task-metrics,connector=*,task=*");

    StateMapping taskStateMapping = getMetric(connectorTaskRule, "status").getStateMapping();
    assertThat(getMetric(connectorTaskRule, "status").getMetricType())
        .isEqualTo(MetricInfo.Type.UPDOWNCOUNTER);
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
  void metricsAreReportedFromKafkaConnectContainer() throws Exception {
    Collection<String> yamlFiles = getAllRulesForSystem("kafka-connect");

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> kafka = KafkaContainer.create(KAFKA_IMAGE);

    GenericContainer<?> kafkaConnect =
        KafkaContainer.create(KAFKA_IMAGE)
            .withKafkaConnect()
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .withCopyToContainer(Transferable.of("first\nsecond\nthird\n"), SOURCE_FILE_PATH);

    copyAgentToTarget(kafkaConnect);
    copyYamlFilesToTarget(kafkaConnect, yamlFiles);

    startWeaverValidation(
        "kafka-connect.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("kafka.connect.")
                .checkRegisteredMetrics(
                    "kafka.connect.",
                    asList(
                        "kafka.connect.worker.connector.count",
                        "kafka.connect.worker.connector.startup.count",
                        "kafka.connect.worker.task.count",
                        "kafka.connect.worker.task.startup.count",
                        "kafka.connect.worker.connector.task.count",
                        "kafka.connect.worker.rebalance.completed.count",
                        "kafka.connect.worker.rebalance.protocol",
                        "kafka.connect.worker.rebalance.epoch",
                        "kafka.connect.worker.rebalance.time.average",
                        "kafka.connect.worker.rebalance.time.max",
                        "kafka.connect.worker.rebalance.active",
                        "kafka.connect.connector.status",
                        "kafka.connect.task.batch.size.average",
                        "kafka.connect.task.batch.size.max",
                        "kafka.connect.task.offset.commit.failure.ratio",
                        "kafka.connect.task.running.ratio",
                        "kafka.connect.task.status",
                        "kafka.connect.sink.offset.commit.completed.count",
                        "kafka.connect.sink.offset.commit.seq",
                        "kafka.connect.sink.offset.commit.skipped.count",
                        "kafka.connect.sink.partition.count",
                        "kafka.connect.sink.put.batch.time.average",
                        "kafka.connect.sink.put.batch.time.max",
                        "kafka.connect.sink.record.active.count",
                        "kafka.connect.sink.record.read.count",
                        "kafka.connect.sink.record.send.count",
                        "kafka.connect.source.poll.batch.time.average",
                        "kafka.connect.source.poll.batch.time.max",
                        "kafka.connect.source.record.active.count",
                        "kafka.connect.source.record.poll.count",
                        "kafka.connect.source.record.write.count",
                        "kafka.connect.task.error.deadletterqueue.produce.failure.count",
                        "kafka.connect.task.error.deadletterqueue.produce.request.count",
                        "kafka.connect.task.error.last.error.timestamp",
                        "kafka.connect.task.error.logged.count",
                        "kafka.connect.task.error.record.error.count",
                        "kafka.connect.task.error.record.failure.count",
                        "kafka.connect.task.error.record.skipped.count",
                        "kafka.connect.task.error.retry.count"),
                    OPTIONAL_APACHE_METRICS)
                .checkRegisteredAttributes(
                    "kafka.connect.",
                    asList(
                        "kafka.connect.connector",
                        "kafka.connect.task.id",
                        "kafka.connect.worker.connector.startup.result",
                        "kafka.connect.worker.task.startup.result",
                        "kafka.connect.worker.connector.task.state",
                        "kafka.connect.protocol.state",
                        "kafka.connect.worker.rebalance.state",
                        "kafka.connect.connector.state",
                        "kafka.connect.task.state"),
                    emptyList()));

    startTarget(kafkaConnect, singletonList(kafka));

    String connectUrl = connectUrl(kafkaConnect);
    createConnector(connectUrl, sourceConnectorConfig());
    createConnector(connectUrl, sinkConnectorConfig());

    awaitConnectorRunning(connectUrl, SOURCE_CONNECTOR);
    awaitConnectorRunning(connectUrl, SINK_CONNECTOR);

    kafkaConnect.execInContainer("sh", "-c", "printf 'fourth\\n' >> " + SOURCE_FILE_PATH);

    verifyMetrics(createKafkaConnectMetricsVerifier(), Duration.ofMinutes(5));
  }

  private JmxConfig loadKafkaConnectConfig() throws Exception {
    InternalMetricsDefinitions definitions = new InternalMetricsDefinitions(        getClass()
        .getClassLoader());

    Set<String> rules = definitions.getRulesForSystem("kafka-connect", true, true);
    assertThat(rules).hasSize(1);

    try (InputStream input =
        getClass()
            .getClassLoader()
            .getResourceAsStream(rules.stream().findFirst().get())) {
      assertThat(input).isNotNull();
      return RuleParser.get().loadConfig(input);
    }
  }

  private static MetricsVerifier createKafkaConnectMetricsVerifier() {
    return MetricsVerifier.create()
        // Worker metrics
        .add(
            "kafka.connect.worker.connector.count",
            metric ->
                metric
                    .hasDescription("The number of connectors run in this worker.")
                    .hasUnit("{connector}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.connector.startup.count",
            metric ->
                metric
                    .hasDescription("The number of connector starts for this worker.")
                    .hasUnit("{startup}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(
                        attributeWithAnyValue("kafka.connect.worker.connector.startup.result")))
        .add(
            "kafka.connect.worker.task.count",
            metric ->
                metric
                    .hasDescription("The number of currently running tasks for this worker.")
                    .hasUnit("{task}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.task.startup.count",
            metric ->
                metric
                    .hasDescription("The number of task starts for this worker.")
                    .hasUnit("{startup}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(
                        attributeWithAnyValue("kafka.connect.worker.task.startup.result")))
        // Worker connector task metrics
        .add(
            "kafka.connect.worker.connector.task.count",
            metric ->
                metric
                    .hasDescription("The number of tasks of the connector on the worker by state.")
                    .hasUnit("{task}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.worker.connector.task.state"))))
        // Worker rebalance metrics
        .add(
            "kafka.connect.worker.rebalance.completed.count",
            metric ->
                metric
                    .hasDescription("The number of rebalances completed by this worker.")
                    .hasUnit("{rebalance}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.rebalance.protocol",
            metric ->
                metric
                    .hasDescription("The Connect protocol used by this cluster.")
                    .hasUnit("1")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(
                        attributeWithAnyValue("kafka.connect.protocol.state")))
        .add(
            "kafka.connect.worker.rebalance.epoch",
            metric ->
                metric
                    .hasDescription("The epoch or generation number of this worker.")
                    .hasUnit("{epoch}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.rebalance.time.average",
            metric ->
                metric
                    .hasDescription(
                        "The average time in seconds spent by this worker to rebalance.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.rebalance.time.max",
            metric ->
                metric
                    .hasDescription(
                        "The maximum time in seconds spent by this worker to rebalance.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.connect.worker.rebalance.active",
            metric ->
                metric
                    .hasDescription("Whether this worker is currently rebalancing.")
                    .hasUnit("1")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(
                        attributeWithAnyValue("kafka.connect.worker.rebalance.state")))
        // Connector metrics
        .add(
            "kafka.connect.connector.status",
            metric ->
                metric
                    .hasDescription("Connector lifecycle state indicator.")
                    .hasUnit("1")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.connector.state"))))
        // Connector task metrics
        .add(
            "kafka.connect.task.batch.size.average",
            metric ->
                metric
                    .hasDescription(
                        "The average number of records in the batches the task has processed so far.")
                    .hasUnit("{record}")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.batch.size.max",
            metric ->
                metric
                    .hasDescription(
                        "The number of records in the largest batch the task has processed so far.")
                    .hasUnit("{record}")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.offset.commit.failure.ratio",
            metric ->
                metric
                    .hasDescription(
                        "The average ratio of this task's offset commit attempts that failed.")
                    .hasUnit("1")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.running.ratio",
            metric ->
                metric
                    .hasDescription(
                        "The fraction of time this task has spent in the running state.")
                    .hasUnit("1")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.status",
            metric ->
                metric
                    .hasDescription("The status of the connector task.")
                    .hasUnit("1")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"),
                            attributeWithAnyValue("kafka.connect.task.state"))))
        // Sink task metrics
        .add(
            "kafka.connect.sink.offset.commit.completed.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of offset commit completions that were completed successfully.")
                    .hasUnit("{commit}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.offset.commit.seq",
            metric ->
                metric
                    .hasDescription("The current sequence number for offset commits.")
                    .hasUnit("{sequence}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.offset.commit.skipped.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of offset commit completions that were received too late and skipped/ignored.")
                    .hasUnit("{commit}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.partition.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of topic partitions assigned to this task belonging to the named sink connector in this worker.")
                    .hasUnit("{partition}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.put.batch.time.average",
            metric ->
                metric
                    .hasDescription(
                        "The average time taken by this task to put a batch of sinks records.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.put.batch.time.max",
            metric ->
                metric
                    .hasDescription(
                        "The maximum time taken by this task to put a batch of sinks records.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.record.active.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of records that have been read from Kafka but not yet completely committed/flushed/acknowledged by the sink task.")
                    .hasUnit("{record}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.record.read.count",
            metric ->
                metric
                    .hasDescription(
                        "The count number of records read from Kafka by this task belonging to the named sink connector in this worker, since the task was last restarted.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.sink.record.send.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of records output from the transformations and sent/put to this task belonging to the named sink connector in this worker, since the task was last restarted.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        // Source task metrics
        .add(
            "kafka.connect.source.poll.batch.time.average",
            metric ->
                metric
                    .hasDescription(
                        "The average time in seconds taken by this task to poll for a batch of source records.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.source.poll.batch.time.max",
            metric ->
                metric
                    .hasDescription(
                        "The maximum time in seconds taken by this task to poll for a batch of source records.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.source.record.active.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of records that have been produced by this task but not yet completely written to Kafka.")
                    .hasUnit("{record}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.source.record.poll.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of records produced/polled (before transformation) by this task belonging to the named source connector in this worker.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.source.record.write.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of records output written to Kafka for this task belonging to the named source connector in this worker, since the task was last restarted. This is after transformations are applied, and excludes any records filtered out by the transformations.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        // Task error metrics
        .add(
            "kafka.connect.task.error.deadletterqueue.produce.failure.count",
            metric ->
                metric
                    .hasDescription("The number of failed writes to the dead letter queue.")
                    .hasUnit("{failure}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.deadletterqueue.produce.request.count",
            metric ->
                metric
                    .hasDescription("The number of attempted writes to the dead letter queue.")
                    .hasUnit("{request}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.last.error.timestamp",
            metric ->
                metric
                    .hasDescription("The epoch timestamp when this task last encountered an error.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.logged.count",
            metric ->
                metric
                    .hasDescription("The number of errors that were logged.")
                    .hasUnit("{error}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.record.error.count",
            metric ->
                metric
                    .hasDescription("The number of record processing errors in this task.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.record.failure.count",
            metric ->
                metric
                    .hasDescription("The number of record processing failures in this task.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.record.skipped.count",
            metric ->
                metric
                    .hasDescription("The number of records skipped due to errors.")
                    .hasUnit("{record}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))))
        .add(
            "kafka.connect.task.error.retry.count",
            metric ->
                metric
                    .hasDescription("The number of operations retried.")
                    .hasUnit("{retry}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("kafka.connect.connector"),
                            attributeWithAnyValue("kafka.connect.task.id"))));
  }

  private static JmxRule getRuleForBean(JmxConfig config, String bean) {
    return config.getRules().stream()
        .filter(rule -> rule.getBeans().contains(bean))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing rule for bean " + bean));
  }

  private static Metric getMetric(JmxRule rule, String metricKey) {
    Metric metric = rule.getMapping().get(metricKey);
    assertThat(metric).isNotNull();
    return metric;
  }

  private static String connectUrl(GenericContainer<?> container) {
    return "http://" + container.getHost() + ":" + container.getFirstMappedPort();
  }

  private static void createConnector(String connectUrl, String connectorConfigJson) {
    AggregatedHttpResponse response =
        sendRequest(HttpMethod.POST, connectUrl + "/connectors", connectorConfigJson);
    assertThat(response.status().code()).isIn(200, 201, 409);
  }

  private static void awaitConnectorRunning(String connectUrl, String connectorName) {
    await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              AggregatedHttpResponse response =
                  sendRequest(
                      HttpMethod.GET,
                      connectUrl + "/connectors/" + connectorName + "/status",
                      null);
              assertThat(response.status().code()).isEqualTo(200);
              assertThat(response.contentUtf8()).contains("\"state\":\"RUNNING\"");
            });
  }

  private static AggregatedHttpResponse sendRequest(HttpMethod method, String url, String body) {
    AggregatedHttpRequest request =
        body != null
            ? AggregatedHttpRequest.of(method, url, MediaType.JSON, body)
            : AggregatedHttpRequest.of(method, url);
    return client.execute(request).aggregate().join();
  }

  private static String sourceConnectorConfig() {
    return "{"
        + "\"name\":\""
        + SOURCE_CONNECTOR
        + "\","
        + "\"config\":{"
        + "\"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\","
        + "\"tasks.max\":\"1\","
        + "\"topic\":\""
        + TOPIC
        + "\","
        + "\"file\":\""
        + SOURCE_FILE_PATH
        + "\","
        + "\"errors.tolerance\":\"all\","
        + "\"errors.log.enable\":\"true\","
        + "\"errors.deadletterqueue.topic.name\":\""
        + DLQ_TOPIC
        + "\","
        + "\"errors.deadletterqueue.topic.replication.factor\":\"1\""
        + "}"
        + "}";
  }

  private static String sinkConnectorConfig() {
    return "{"
        + "\"name\":\""
        + SINK_CONNECTOR
        + "\","
        + "\"config\":{"
        + "\"connector.class\":\"org.apache.kafka.connect.file.FileStreamSinkConnector\","
        + "\"tasks.max\":\"1\","
        + "\"topics\":\""
        + TOPIC
        + "\","
        + "\"file\":\""
        + SINK_FILE_PATH
        + "\","
        + "\"errors.tolerance\":\"all\","
        + "\"errors.log.enable\":\"true\","
        + "\"errors.deadletterqueue.topic.name\":\""
        + DLQ_TOPIC
        + "\","
        + "\"errors.deadletterqueue.topic.replication.factor\":\"1\","
        + "\"transforms\":\"extract\","
        + "\"transforms.extract.type\":\"org.apache.kafka.connect.transforms.ExtractField$Value\","
        + "\"transforms.extract.field\":\"missing\""
        + "}"
        + "}";
  }
}
