/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

public class KafkaContainerFactory {

  private static final int KAFKA_CONTROLLER_PORT = 9093;

  private static final String KAFKA_SERVER_PROPERTIES_PATH =
      "/opt/kafka/config/kraft/server.properties";

  private KafkaContainerFactory() {}

  public static GenericContainer<?> createKafkaContainer(String image, String alias, int port) {
    String kafkaCommand =
        "/opt/kafka/bin/kafka-storage.sh format -t $(/opt/kafka/bin/kafka-storage.sh random-uuid) -c "
            + KAFKA_SERVER_PROPERTIES_PATH
            + " && /opt/kafka/bin/kafka-server-start.sh "
            + KAFKA_SERVER_PROPERTIES_PATH;

    return new GenericContainer<>(image)
            .withNetworkAliases(alias)
            .withCopyToContainer(
                Transferable.of(kafkaServerProperties(alias, port)), KAFKA_SERVER_PROPERTIES_PATH)
            .withExposedPorts(port)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand("-c", kafkaCommand)
            .withStartupTimeout(Duration.ofMinutes(1))
            .waitingFor(Wait.forListeningPort());
  }

  private static String kafkaServerProperties(String alias, int port) {
    return String.join(
        "\n",
        "process.roles=broker,controller",
        "node.id=1",
        "controller.quorum.voters=1@" + alias + ":" + KAFKA_CONTROLLER_PORT,
        "listeners=PLAINTEXT://0.0.0.0:"
            + port
            + ",CONTROLLER://0.0.0.0:"
            + KAFKA_CONTROLLER_PORT,
        "listener.security.protocol.map=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT",
        "inter.broker.listener.name=PLAINTEXT",
        "controller.listener.names=CONTROLLER",
        "advertised.listeners=PLAINTEXT://" + alias + ":" + port,
        "log.dirs=/tmp/kraft-combined-logs",
        "num.partitions=1",
        "offsets.topic.replication.factor=1",
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1",
        "group.initial.rebalance.delay.ms=0",
        "auto.create.topics.enable=true");
  }

}
