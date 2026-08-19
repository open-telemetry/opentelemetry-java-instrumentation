/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class KafkaBrokerTest extends TargetSystemTest {

  private static final String KAFKA_IMAGE = "apache/kafka:3.8.0";

  @Test
  void kafkaBroker() {
    List<String> yamlFiles = singletonList("experimental-kafka-broker.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        KafkaContainerFactory.createKafkaContainer(KAFKA_IMAGE)
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    // TODO: add weaver validation

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {

    AttributeMatcherGroup fetchType = attributeGroup(attribute("type", "fetch"));
    AttributeMatcherGroup produceType = attributeGroup(attribute("type", "produce"));

    AttributeMatcherGroup requestTypeProduce = attributeGroup(attribute("type", "Produce"));
    AttributeMatcherGroup requestTypeFetchConsumer =
        attributeGroup(attribute("type", "FetchConsumer"));
    AttributeMatcherGroup requestTypeFetchFollower =
        attributeGroup(attribute("type", "FetchFollower"));

    return MetricsVerifier.create()
        .add(
            "kafka.message.count",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{messages}")
                    .hasDescription("The number of messages received by the broker")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.request.count",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{requests}")
                    .hasDescription("The number of requests received by the broker")
                    .hasDataPointsWithAttributes(fetchType, produceType))
        .add(
            "kafka.request.failed",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{requests}")
                    .hasDescription("The number of requests to the broker resulting in a failure")
                    .hasDataPointsWithAttributes(fetchType, produceType))
        .add(
            "kafka.request.time.total",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("ms")
                    .hasDescription("The total time the broker has taken to service requests")
                    .hasDataPointsWithAttributes(
                        requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
        .add(
            "kafka.request.time.50p",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("ms")
                    .hasDescription(
                        "The 50th percentile time the broker has taken to service requests")
                    .hasDataPointsWithAttributes(
                        requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
        .add(
            "kafka.request.time.99p",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("ms")
                    .hasDescription(
                        "The 99th percentile time the broker has taken to service requests")
                    .hasDataPointsWithAttributes(
                        requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
        .add(
            "kafka.request.queue",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{requests}")
                    .hasDescription("Size of the request queue")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.network.io",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("By")
                    .hasDescription("The bytes received or sent by the broker")
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("direction", "in")),
                        attributeGroup(attribute("direction", "out"))))
        .add(
            "kafka.purgatory.size",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{requests}")
                    .hasDescription("The number of requests waiting in purgatory")
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("type", "Produce")),
                        attributeGroup(attribute("type", "Fetch"))))
        .add(
            "kafka.partition.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{partitions}")
                    .hasDescription("The number of partitions on the broker")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.partition.offline",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{partitions}")
                    .hasDescription("The number of partitions offline")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.partition.underReplicated",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{partitions}")
                    .hasDescription("The number of under replicated partitions")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.isr.operation.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{operations}")
                    .hasDescription("The number of in-sync replica shrink and expand operations")
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("operation", "shrink")),
                        attributeGroup(attribute("operation", "expand"))))
        .add(
            "kafka.lag.max",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("{messages}")
                    .hasDescription(
                        "The max lag in messages between follower and leader replicas")
                    .hasDataPointsWithoutAttributes())
        // TODO: kafka.leaderElection.count and kafka.leaderElection.unclean.count are defined in
        // the YAML (kafka.controller:type=ControllerStats) but are not reported by KRaft brokers.
        // These should be enabled once KRaft exposes those MBeans or a ZooKeeper-mode test is added.
        // .add(
        //     "kafka.leaderElection.count",
        //     metric ->
        //         metric
        //             .isCounter()
        //             .hasUnit("{elections}")
        //             .hasDescription("The leader election count")
        //             .hasDataPointsWithoutAttributes())
        // .add(
        //     "kafka.leaderElection.unclean.count",
        //     metric ->
        //         metric
        //             .isCounter()
        //             .hasUnit("{elections}")
        //             .hasDescription(
        //                 "Unclean leader election count - increasing indicates broker failures")
        //             .hasDataPointsWithoutAttributes())
        .add(
            "kafka.controller.active.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{controllers}")
                    .hasDescription("The number of controllers active on the broker")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.logs.flush.Count",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("ms")
                    .hasDescription("Log flush count")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.logs.flush.time.50p",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("ms")
                    .hasDescription("Log flush time - 50th percentile")
                    .hasDataPointsWithoutAttributes())
        .add(
            "kafka.logs.flush.time.99p",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("ms")
                    .hasDescription("Log flush time - 99th percentile")
                    .hasDataPointsWithoutAttributes());
  }
}
