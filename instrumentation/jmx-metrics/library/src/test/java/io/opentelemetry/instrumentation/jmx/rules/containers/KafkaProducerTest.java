/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.containers;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class KafkaProducerTest extends TargetSystemTest {

  @Test
  void kafkaProducer() {
    KafkaContainer broker = KafkaContainer.create("apache/kafka:3.8.0");
    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(
        javaPropertiesToJvmArgs(otelConfigPropertiesExperimentalOptIn("kafka.producer.*")));

    GenericContainer<?> producer =
        new GenericContainer<>("apache/kafka:3.8.0")
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand(
                "-c",
                "/opt/kafka/bin/kafka-producer-perf-test.sh"
                    + " --topic producer-topic --num-records 1000000 --record-size 100"
                    + " --throughput 10 --producer-props"
                    + " bootstrap.servers=kafka:9092 client.id=example-producer")
            .waitingFor(
                Wait.forLogMessage(".* records sent,.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    copyAgentToTarget(producer);

    startWeaverValidation(
        "kafka-producer.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("kafka.producer.")
                .checkRegisteredMetrics(
                    "kafka.producer.",
                    asList(
                        "kafka.producer.io.wait.time.average",
                        "kafka.producer.outgoing.byte.rate",
                        "kafka.producer.request.latency.average",
                        "kafka.producer.request.rate",
                        "kafka.producer.response.rate",
                        "kafka.producer.topic.byte.rate",
                        "kafka.producer.topic.compression.ratio",
                        "kafka.producer.topic.record.error.rate",
                        "kafka.producer.topic.record.retry.rate",
                        "kafka.producer.topic.record.send.rate"),
                    emptyList())
                .checkRegisteredAttributes(
                    "messaging.",
                    asList("messaging.client.id", "messaging.destination.name"),
                    emptyList()));

    startTarget(producer, singletonList(broker));
    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher client = attribute("messaging.client.id", "example-producer");
    AttributeMatcher topic = attribute("messaging.destination.name", "producer-topic");
    return MetricsVerifier.create()
        .add(
            "kafka.producer.io.wait.time.average",
            metric ->
                metric
                    .hasDescription(
                        "The average time the producer I/O thread waits for a socket to be ready.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(client))
        .add(
            "kafka.producer.outgoing.byte.rate",
            metric ->
                metric
                    .hasDescription("The average number of bytes sent per second to all brokers.")
                    .hasUnit("By/s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(client))
        .add(
            "kafka.producer.request.latency.average",
            metric ->
                metric
                    .hasDescription("The average request latency.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(client))
        .add(
            "kafka.producer.request.rate",
            metric ->
                metric
                    .hasDescription("The average number of requests sent per second.")
                    .hasUnit("{request}/s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(client))
        .add(
            "kafka.producer.response.rate",
            metric ->
                metric
                    .hasDescription("The average number of responses received per second.")
                    .hasUnit("{response}/s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(client))
        .add(
            "kafka.producer.topic.byte.rate",
            metric ->
                metric
                    .hasDescription("The average number of bytes sent per second to the topic.")
                    .hasUnit("By/s")
                    .isGauge()
                    .hasDataPointsWithAttributes(attributeGroup(client, topic)))
        .add(
            "kafka.producer.topic.compression.ratio",
            metric ->
                metric
                    .hasDescription(
                        "The average ratio of compressed to uncompressed record batch size for the topic.")
                    .hasUnit("1")
                    .isGauge()
                    .hasDataPointsWithAttributes(attributeGroup(client, topic)))
        .add(
            "kafka.producer.topic.record.error.rate",
            metric ->
                metric
                    .hasDescription(
                        "The average number of record send errors per second for the topic.")
                    .hasUnit("{record}/s")
                    .isGauge()
                    .hasDataPointsWithAttributes(attributeGroup(client, topic)))
        .add(
            "kafka.producer.topic.record.retry.rate",
            metric ->
                metric
                    .hasDescription(
                        "The average number of retried record sends per second for the topic.")
                    .hasUnit("{record}/s")
                    .isGauge()
                    .hasDataPointsWithAttributes(attributeGroup(client, topic)))
        .add(
            "kafka.producer.topic.record.send.rate",
            metric ->
                metric
                    .hasDescription("The average number of records sent per second to the topic.")
                    .hasUnit("{record}/s")
                    .isGauge()
                    .hasDataPointsWithAttributes(attributeGroup(client, topic)));
  }
}
