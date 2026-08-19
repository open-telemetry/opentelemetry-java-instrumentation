/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

class KafkaContainerFactory {

  private static final int KAFKA_CONTROLLER_PORT = 9093;

  private static final String KAFKA_ALIAS = "kafka";
  private static final int KAFKA_PORT = 9092;

  private static final String CONNECT_ALIAS = "kafka-connect";
  private static final int CONNECT_PORT = 8083;
  private static final String CONNECT_PROPERTIES_PATH =
      "/opt/kafka/config/connect-distributed.properties";

  private static final String KAFKA_SERVER_PROPERTIES_PATH =
      "/opt/kafka/config/kraft/server.properties";

  private KafkaContainerFactory() {}

  /**
   * Creates a single-node kafka instance
   *
   * @param image docker image name
   * @return single node kafka node container
   */
  public static GenericContainer<?> createKafkaContainer(String image) {
    String kafkaCommand =
        "/opt/kafka/bin/kafka-storage.sh format -t $(/opt/kafka/bin/kafka-storage.sh random-uuid) -c "
            + KAFKA_SERVER_PROPERTIES_PATH
            + " && /opt/kafka/bin/kafka-server-start.sh "
            + KAFKA_SERVER_PROPERTIES_PATH;

    return new GenericContainer<>(image)
        .withNetworkAliases(KAFKA_ALIAS)
        .withCopyToContainer(Transferable.of(kafkaServerProperties()), KAFKA_SERVER_PROPERTIES_PATH)
        .withExposedPorts(KAFKA_PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
        .withCommand("-c", kafkaCommand)
        .withStartupTimeout(Duration.ofMinutes(1))
        .waitingFor(Wait.forListeningPort());
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

  /**
   * Creates a single Kafka connect node
   *
   * @param image docker image name
   * @return kafka connect container
   */
  static GenericContainer<?> createKafkaConnectContainer(String image) {
    return new GenericContainer<>(image)
        .withNetworkAliases(CONNECT_ALIAS)
        .withCopyToContainer(Transferable.of(connectWorkerProperties()), CONNECT_PROPERTIES_PATH)
        .withExposedPorts(CONNECT_PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
        .withCommand("-c", "/opt/kafka/bin/connect-distributed.sh " + CONNECT_PROPERTIES_PATH)
        .withStartupTimeout(Duration.ofMinutes(1))
        .waitingFor(
            Wait.forHttp("/connectors")
                .forPort(CONNECT_PORT)
                .withStartupTimeout(Duration.ofMinutes(5)));
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
}
