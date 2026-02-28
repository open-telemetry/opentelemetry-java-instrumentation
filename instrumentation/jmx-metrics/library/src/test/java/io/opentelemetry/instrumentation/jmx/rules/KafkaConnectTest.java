/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

class KafkaConnectTest extends TargetSystemTest {
  private static final String APACHE_KAFKA_IMAGE = "apache/kafka:3.8.0";
  private static final int KAFKA_PORT = 9092;
  private static final int KAFKA_CONTROLLER_PORT = 9093;
  private static final int CONNECT_PORT = 8083;
  private static final String KAFKA_ALIAS = "kafka";
  private static final String CONNECT_ALIAS = "kafka-connect";
  private static final String KAFKA_SERVER_PROPERTIES_PATH =
      "/opt/kafka/config/kraft/server.properties";
  private static final String CONNECT_PROPERTIES_PATH =
      "/opt/kafka/config/connect-distributed.properties";
  private static final String SOURCE_CONNECTOR = "file-source";
  private static final String SINK_CONNECTOR = "file-sink";
  private static final String SOURCE_FILE_PATH = "/tmp/source.txt";
  private static final String SINK_FILE_PATH = "/tmp/sink.txt";
  private static final String TOPIC = "connect-test-topic";
  private static final String DLQ_TOPIC = "connect-dead-letter";
  private static final Set<String> OPTIONAL_APACHE_METRICS = new HashSet<>();

  static {
    // Apache Kafka Connect 3.8 file connectors do not expose these metrics.
    Collections.addAll(
        OPTIONAL_APACHE_METRICS,
        "kafka.connect.sink.record.lag.max",
        "kafka.connect.task.offset.commit.time.avg",
        "kafka.connect.task.offset.commit.time.max",
        "kafka.connect.source.transaction.size.avg",
        "kafka.connect.source.transaction.size.max",
        "kafka.connect.source.transaction.size.min");
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
  void metricsAreReportedFromKafkaConnectContainer() throws Exception {
    List<String> yamlFiles = singletonList("kafka-connect.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    Set<String> expectedCreatedMetrics = loadKafkaConnectMetricNames(false);
    Set<String> registeredMetrics = ConcurrentHashMap.newKeySet();

    String kafkaCommand =
        "/opt/kafka/bin/kafka-storage.sh format -t $(/opt/kafka/bin/kafka-storage.sh random-uuid) -c "
            + KAFKA_SERVER_PROPERTIES_PATH
            + " && /opt/kafka/bin/kafka-server-start.sh "
            + KAFKA_SERVER_PROPERTIES_PATH;

    GenericContainer<?> kafka =
        new GenericContainer<>(APACHE_KAFKA_IMAGE)
            .withNetworkAliases(KAFKA_ALIAS)
            .withCopyToContainer(
                Transferable.of(kafkaServerProperties()), KAFKA_SERVER_PROPERTIES_PATH)
            .withExposedPorts(KAFKA_PORT)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand("-c", kafkaCommand)
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(Wait.forListeningPort());

    GenericContainer<?> kafkaConnect =
        new GenericContainer<>(APACHE_KAFKA_IMAGE)
            .withNetworkAliases(CONNECT_ALIAS)
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .withCopyToContainer(
                Transferable.of(connectWorkerProperties()), CONNECT_PROPERTIES_PATH)
            .withCopyToContainer(Transferable.of("first\nsecond\nthird\n"), SOURCE_FILE_PATH)
            .withExposedPorts(CONNECT_PORT)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand("-c", "/opt/kafka/bin/connect-distributed.sh " + CONNECT_PROPERTIES_PATH)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer(frame -> recordMetricRegistrations(frame, registeredMetrics))
            .waitingFor(
                Wait.forHttp("/connectors")
                    .forPort(CONNECT_PORT)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    copyAgentToTarget(kafkaConnect);
    copyYamlFilesToTarget(kafkaConnect, yamlFiles);

    startTarget(kafkaConnect, singletonList(kafka));

    String connectUrl = connectUrl(kafkaConnect);
    createConnector(connectUrl, sourceConnectorConfig());
    createConnector(connectUrl, sinkConnectorConfig());

    awaitConnectorRunning(connectUrl, SOURCE_CONNECTOR);
    awaitConnectorRunning(connectUrl, SINK_CONNECTOR);

    kafkaConnect.execInContainer("sh", "-c", "printf 'fourth\\n' >> " + SOURCE_FILE_PATH);

    awaitMetricRegistrations(expectedCreatedMetrics, registeredMetrics);
    verifyMetrics(createKafkaConnectMetricsVerifier());
  }

  private io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig loadKafkaConnectConfig()
      throws Exception {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("jmx/rules/kafka-connect.yaml")) {
      assertThat(input).isNotNull();
      return io.opentelemetry.instrumentation.jmx.internal.yaml.RuleParser.get().loadConfig(input);
    }
  }

  private Set<String> loadKafkaConnectMetricNames(boolean includeOptional) throws Exception {
    io.opentelemetry.instrumentation.jmx.internal.yaml.JmxConfig config = loadKafkaConnectConfig();
    Set<String> metricNames = new TreeSet<>();
    for (io.opentelemetry.instrumentation.jmx.internal.yaml.JmxRule rule : config.getRules()) {
      String prefix = rule.getPrefix();
      for (Map.Entry<String, io.opentelemetry.instrumentation.jmx.internal.yaml.Metric> entry :
          rule.getMapping().entrySet()) {
        io.opentelemetry.instrumentation.jmx.internal.yaml.Metric metric = entry.getValue();
        String baseName =
            metric == null || metric.getMetric() == null ? entry.getKey() : metric.getMetric();
        metricNames.add(prefix == null ? baseName : prefix + baseName);
      }
    }
    if (!includeOptional) {
      metricNames.removeAll(OPTIONAL_APACHE_METRICS);
    }
    return metricNames;
  }

  private static void awaitMetricRegistrations(
      Set<String> expectedMetrics, Set<String> registeredMetrics) {
    await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(registeredMetrics).containsAll(expectedMetrics));
  }

  private static void recordMetricRegistrations(OutputFrame frame, Set<String> registeredMetrics) {
    if (frame == null) {
      return;
    }
    String payload = frame.getUtf8String();
    if (payload == null || payload.isEmpty()) {
      return;
    }
    String[] lines = payload.split("\\r?\\n");
    for (String line : lines) {
      int markerIndex = line.indexOf("MetricRegistrar - Created");
      if (markerIndex < 0) {
        continue;
      }
      int forIndex = line.indexOf(" for ", markerIndex);
      if (forIndex < 0) {
        continue;
      }
      String metricName = line.substring(forIndex + 5).trim();
      if (!metricName.isEmpty()) {
        registeredMetrics.add(metricName);
      }
    }
  }

  private static String kafkaServerProperties() {
    return String.join(
        "\n",
        "process.roles=broker,controller",
        "node.id=1",
        "controller.quorum.voters=1@" + KAFKA_ALIAS + ":" + KAFKA_CONTROLLER_PORT,
        "listeners=PLAINTEXT://0.0.0.0:"
            + KAFKA_PORT
            + ",CONTROLLER://0.0.0.0:"
            + KAFKA_CONTROLLER_PORT,
        "listener.security.protocol.map=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT",
        "inter.broker.listener.name=PLAINTEXT",
        "controller.listener.names=CONTROLLER",
        "advertised.listeners=PLAINTEXT://" + KAFKA_ALIAS + ":" + KAFKA_PORT,
        "log.dirs=/tmp/kraft-combined-logs",
        "num.partitions=1",
        "offsets.topic.replication.factor=1",
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1",
        "group.initial.rebalance.delay.ms=0",
        "auto.create.topics.enable=true");
  }

  private static String connectWorkerProperties() {
    return String.join(
        "\n",
        "bootstrap.servers=" + KAFKA_ALIAS + ":" + KAFKA_PORT,
        "group.id=connect-cluster",
        "key.converter=org.apache.kafka.connect.storage.StringConverter",
        "value.converter=org.apache.kafka.connect.storage.StringConverter",
        "key.converter.schemas.enable=false",
        "value.converter.schemas.enable=false",
        "offset.storage.topic=connect-offsets",
        "config.storage.topic=connect-configs",
        "status.storage.topic=connect-status",
        "offset.storage.replication.factor=1",
        "config.storage.replication.factor=1",
        "status.storage.replication.factor=1",
        "plugin.path=/opt/kafka/libs",
        "rest.host.name=0.0.0.0",
        "rest.advertised.host.name=" + CONNECT_ALIAS,
        "listeners=http://0.0.0.0:" + CONNECT_PORT);
  }

  private MetricsVerifier createKafkaConnectMetricsVerifier() throws Exception {
    Set<String> metricNames = loadKafkaConnectMetricNames(false);

    MetricsVerifier verifier = MetricsVerifier.create().disableStrictMode();
    for (String metricName : metricNames) {
      verifier.add(metricName, metric -> {});
    }
    return verifier;
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

  private static String connectUrl(GenericContainer<?> container) {
    return "http://" + container.getHost() + ":" + container.getMappedPort(CONNECT_PORT);
  }

  private static void createConnector(String connectUrl, String connectorConfigJson)
      throws Exception {
    HttpResponseData response =
        sendRequest("POST", connectUrl + "/connectors", connectorConfigJson);
    assertThat(response.statusCode).isIn(200, 201, 409);
  }

  private static void awaitConnectorRunning(String connectUrl, String connectorName) {
    await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              HttpResponseData response =
                  sendRequest("GET", connectUrl + "/connectors/" + connectorName + "/status", null);
              assertThat(response.statusCode).isEqualTo(200);
              assertThat(response.body).contains("\"state\":\"RUNNING\"");
            });
  }

  private static HttpResponseData sendRequest(String method, String url, String body)
      throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod(method);
    connection.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
    connection.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
    connection.setDoInput(true);
    if (body != null) {
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json");
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
      try (OutputStream output = connection.getOutputStream()) {
        output.write(bytes);
      }
    }
    int statusCode = connection.getResponseCode();
    String responseBody = readResponse(connection);
    connection.disconnect();
    return new HttpResponseData(statusCode, responseBody);
  }

  private static String readResponse(HttpURLConnection connection) throws IOException {
    InputStream stream =
        connection.getResponseCode() >= 400
            ? connection.getErrorStream()
            : connection.getInputStream();
    if (stream == null) {
      return "";
    }
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return output.toString(StandardCharsets.UTF_8.name());
    } finally {
      try {
        stream.close();
      } catch (IOException ignored) {
        // best effort cleanup
      }
    }
  }

  private static class HttpResponseData {
    private final int statusCode;
    private final String body;

    private HttpResponseData(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }
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
