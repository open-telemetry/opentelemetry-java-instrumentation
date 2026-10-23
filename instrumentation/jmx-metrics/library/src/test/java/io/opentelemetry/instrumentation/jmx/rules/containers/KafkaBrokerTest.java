/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.containers;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_IO_DIRECTION;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class KafkaBrokerTest extends TargetSystemTest {

  @Test
  void kafkaBroker() {
    doTest("apache/kafka:3.8.0", null);
  }

  @Test
  void kafkaBrokerZookeeper() {
    // intentionally testing a rather old kafka version to ensure common metrics are still reported
    doTest("bitnamilegacy/kafka:2.8.1", "zookeeper:3.5");
  }

  private void doTest(String image, String zookeeperImage) {
    Collection<String> yamlFiles = getAllRulesForSystem("kafka-broker");

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    KafkaContainer target =
        KafkaContainer.create(image).withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startWeaverValidation(
        "kafka-broker.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("kafka.")
                .checkRegisteredMetrics(
                    "kafka.",
                    reportedMetrics(zookeeperImage != null),
                    optionalMetrics(zookeeperImage != null))
                .checkRegisteredAttributes(
                    "kafka.",
                    asList("kafka.request.type", "kafka.topic.name", "kafka.server.isr.operation"),
                    emptyList()));

    List<GenericContainer<?>> dependencies = emptyList();
    if (zookeeperImage != null) {
      GenericContainer<?> zookeeper =
          new GenericContainer<>(zookeeperImage)
              .withNetworkAliases("zookeeper")
              .withExposedPorts(2181)
              .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(1)));

      target.withZookeeper("zookeeper", 2181);
      dependencies = singletonList(zookeeper);
    }
    startTarget(target, dependencies);

    verifyMetrics(createMetricsVerifier(zookeeperImage != null));
  }

  private static List<String> reportedMetrics(boolean useZookeeper) {
    List<String> metrics =
        new ArrayList<>(
            asList(
                "kafka.server.messages",
                "kafka.server.requests",
                "kafka.server.failed_requests",
                "kafka.server.network.io",
                "kafka.server.purgatory.size",
                "kafka.network.requests",
                "kafka.network.request.duration.p50",
                "kafka.network.request.duration.p99",
                "kafka.network.request.queue",
                "kafka.server.partition.count",
                "kafka.controller.partition.offline",
                "kafka.server.partition.under_replicated",
                "kafka.server.isr.operations",
                "kafka.server.lag.max",
                "kafka.controller.active.count",
                "kafka.log.flushes",
                "kafka.log.flush.duration.p50",
                "kafka.log.flush.duration.p99"));
    if (useZookeeper) {
      metrics.addAll(zookeeperOnlyMetrics());
    }
    return metrics;
  }

  private static List<String> optionalMetrics(boolean useZookeeper) {
    return useZookeeper ? emptyList() : zookeeperOnlyMetrics();
  }

  // those metrics are only reported when using zookeeper
  private static List<String> zookeeperOnlyMetrics() {
    return asList("kafka.controller.leader_elections", "kafka.controller.unclean_leader_elections");
  }

  private static MetricsVerifier createMetricsVerifier(boolean useZookeeper) {

    // topic used to send and consume the sample message
    AttributeMatcher sampleTopic = attribute("kafka.topic.name", "my-topic");
    // internal topic written by the broker when the sample consumer joins a consumer group
    AttributeMatcher offsetsTopic = attribute("kafka.topic.name", "__consumer_offsets");
    // compacted topic used to trigger a failed produce request
    AttributeMatcher compactedTopic = attribute("kafka.topic.name", "my-compacted-topic");

    AttributeMatcherGroup requestTypeProduce =
        attributeGroup(attribute("kafka.request.type", "produce"));
    AttributeMatcherGroup requestTypeFetchConsumer =
        attributeGroup(attribute("kafka.request.type", "fetchconsumer"));
    AttributeMatcherGroup requestTypeFetchFollower =
        attributeGroup(attribute("kafka.request.type", "fetchfollower"));

    MetricsVerifier verifier =
        MetricsVerifier.create()
            .add(
                "kafka.server.messages",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{message}")
                        .hasDescription("The number of messages received by the broker")
                        .hasDataPointsWithAttributes(
                            attributeGroup(sampleTopic), attributeGroup(offsetsTopic)))
            .add(
                "kafka.server.requests",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{request}")
                        .hasDescription("The number of requests received by the broker")
                        .hasDataPointsWithAttributes(
                            attributeGroup(attribute("kafka.request.type", "fetch"), sampleTopic),
                            attributeGroup(attribute("kafka.request.type", "produce"), sampleTopic),
                            attributeGroup(
                                attribute("kafka.request.type", "produce"), offsetsTopic),
                            attributeGroup(
                                attribute("kafka.request.type", "produce"), compactedTopic)))
            .add(
                "kafka.server.failed_requests",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{request}")
                        .hasDescription(
                            "The number of requests to the broker resulting in a failure")
                        // only failed produce requests can be triggered, failed fetch requests are
                        // only reported for unexpected errors
                        .hasDataPointsWithAttributes(
                            attributeGroup(
                                attribute("kafka.request.type", "produce"), compactedTopic)))
            .add(
                "kafka.server.network.io",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("By")
                        .hasDescription("The bytes received or sent by the broker")
                        .hasDataPointsWithAttributes(
                            attributeGroup(
                                attribute(NETWORK_IO_DIRECTION.getKey(), "receive"), sampleTopic),
                            attributeGroup(
                                attribute(NETWORK_IO_DIRECTION.getKey(), "receive"), offsetsTopic),
                            attributeGroup(
                                attribute(NETWORK_IO_DIRECTION.getKey(), "transmit"), sampleTopic)))
            .add(
                "kafka.server.purgatory.size",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{request}")
                        .hasDescription("The number of requests waiting in purgatory")
                        .hasDataPointsWithAttributes(
                            attributeGroup(attribute("kafka.request.type", "produce")),
                            attributeGroup(attribute("kafka.request.type", "fetch"))))
            .add(
                "kafka.server.partition.count",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{partition}")
                        .hasDescription("The number of partitions on the broker")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.server.partition.under_replicated",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{partition}")
                        .hasDescription("The number of under-replicated partitions")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.server.isr.operations",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{operation}")
                        .hasDescription("The number of in-sync replica operations")
                        .hasDataPointsWithAttributes(
                            attributeGroup(attribute("kafka.server.isr.operation", "shrink")),
                            attributeGroup(attribute("kafka.server.isr.operation", "expand"))))
            .add(
                "kafka.server.lag.max",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{message}")
                        .hasDescription(
                            "The max lag in messages between follower and leader replicas")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.network.requests",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{request}")
                        .hasDescription("The number of network requests received by the broker")
                        .hasDataPointsWithAttributes(
                            requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
            .add(
                "kafka.network.request.duration.p50",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription(
                            "The 50th percentile duration the broker has taken to service network requests")
                        .hasDataPointsWithAttributes(
                            requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
            .add(
                "kafka.network.request.duration.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription(
                            "The 99th percentile duration the broker has taken to service network requests")
                        .hasDataPointsWithAttributes(
                            requestTypeProduce, requestTypeFetchConsumer, requestTypeFetchFollower))
            .add(
                "kafka.network.request.queue",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{request}")
                        .hasDescription("Size of the network request queue")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.controller.partition.offline",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{partition}")
                        .hasDescription("The number of partitions offline")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.controller.active.count",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasUnit("{controller}")
                        .hasDescription("The number of controllers active on the broker")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.log.flushes",
                metric ->
                    metric
                        .isCounter()
                        .hasUnit("{flush}")
                        .hasDescription("Log flush count")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.log.flush.duration.p50",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("Log flush duration 50th percentile")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.log.flush.duration.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("Log flush duration 99th percentile")
                        .hasDataPointsWithoutAttributes());

    if (useZookeeper) {
      // those metrics are only reported when using zookeeper
      verifier
          .add(
              "kafka.controller.leader_elections",
              metric ->
                  metric
                      .isCounter()
                      .hasUnit("{election}")
                      .hasDescription("The leader election count")
                      .hasDataPointsWithoutAttributes())
          .add(
              "kafka.controller.unclean_leader_elections",
              metric ->
                  metric
                      .isCounter()
                      .hasUnit("{election}")
                      .hasDescription("Unclean leader election count")
                      .hasDataPointsWithoutAttributes());
    }
    return verifier;
  }
}
