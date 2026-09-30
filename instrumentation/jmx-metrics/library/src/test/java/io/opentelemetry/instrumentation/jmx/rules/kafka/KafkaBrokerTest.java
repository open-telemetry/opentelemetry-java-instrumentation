/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.time.Duration;
import java.util.ArrayList;
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
    List<String> yamlFiles = singletonList("experimental-kafka-broker.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    KafkaContainer target =
        KafkaContainer.create(image).withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    // TODO: add weaver validation

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

  private static MetricsVerifier createMetricsVerifier(boolean useZookeeper) {

    AttributeMatcherGroup fetchType = attributeGroup(attribute("type", "fetch"));
    AttributeMatcherGroup produceType = attributeGroup(attribute("type", "produce"));

    AttributeMatcherGroup requestTypeProduce = attributeGroup(attribute("type", "Produce"));
    AttributeMatcherGroup requestTypeFetchConsumer =
        attributeGroup(attribute("type", "FetchConsumer"));
    AttributeMatcherGroup requestTypeFetchFollower =
        attributeGroup(attribute("type", "FetchFollower"));
    AttributeMatcherGroup isrExpand = attributeGroup(attribute("operation", "expand"));
    AttributeMatcherGroup isrShrink = attributeGroup(attribute("operation", "shrink"));
    AttributeMatcherGroup directionIn = attributeGroup(attribute("direction", "in"));
    AttributeMatcherGroup directionOut = attributeGroup(attribute("direction", "out"));

    MetricsVerifier verifier =
        MetricsVerifier.create()
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
                        .hasDescription(
                            "The number of requests to the broker resulting in a failure")
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
                        .hasDescription(
                            "The number of in-sync replica shrink and expand operations")
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
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.leader.count",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{leader}")
                        .hasDescription("The number of leaders assigned to the broker.")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.isr.operation.rate.1m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{operation}/s")
                        .hasDescription("The one-minute rate of ISR expand and shrink operations.")
                        .hasDataPointsWithAttributes(isrExpand, isrShrink))
            .add(
                "kafka.isr.operation.rate.5m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{operation}/s")
                        .hasDescription("The five-minute rate of ISR expand and shrink operations.")
                        .hasDataPointsWithAttributes(isrExpand, isrShrink))
            .add(
                "kafka.isr.operation.rate.15m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{operation}/s")
                        .hasDescription(
                            "The fifteen-minute rate of ISR expand and shrink operations.")
                        .hasDataPointsWithAttributes(isrExpand, isrShrink))
            .add(
                "kafka.isr.operation.rate.mean",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("{operation}/s")
                        .hasDescription("The mean rate of ISR expand and shrink operations.")
                        .hasDataPointsWithAttributes(isrExpand, isrShrink))
            .add(
                "kafka.network.io.rate.1m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("By/s")
                        .hasDescription("The one-minute inbound or outbound byte rate.")
                        .hasDataPointsWithAttributes(directionIn, directionOut))
            .add(
                "kafka.network.io.rate.5m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("By/s")
                        .hasDescription("The five-minute inbound or outbound byte rate.")
                        .hasDataPointsWithAttributes(directionIn, directionOut))
            .add(
                "kafka.network.io.rate.15m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("By/s")
                        .hasDescription("The fifteen-minute inbound or outbound byte rate.")
                        .hasDataPointsWithAttributes(directionIn, directionOut))
            .add(
                "kafka.network.io.rate.mean",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("By/s")
                        .hasDescription("The mean inbound or outbound byte rate.")
                        .hasDataPointsWithAttributes(directionIn, directionOut))
            .add(
                "kafka.request.total.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("The 99th percentile total request time.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.request.queue.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription(
                            "The 99th percentile time requests spend queued before processing.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.request.local.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("The 99th percentile local processing time for requests.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.request.remote.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("The 99th percentile remote processing time for requests.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.request.response.queue.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription(
                            "The 99th percentile time responses spend queued before send.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.request.response.send.time.p99",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("s")
                        .hasDescription("The 99th percentile response send time.")
                        .hasDataPointsWithOneAttribute(attributeWithAnyValue("type")))
            .add(
                "kafka.network.processor.idle.average",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("1")
                        .hasDescription("The average fraction of time network processors are idle.")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.request.handler.idle.mean",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("1")
                        .hasDescription("The mean fraction of time request handlers are idle.")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.request.handler.idle.1m",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("1")
                        .hasDescription(
                            "The one-minute fraction of time request handlers are idle.")
                        .hasDataPointsWithoutAttributes())
            .add(
                "kafka.network.processor.idle.utilization",
                metric ->
                    metric
                        .isGauge()
                        .hasUnit("1")
                        .hasDescription("The idle fraction of an individual network processor.")
                        .hasDataPointsWithOneAttribute(
                            attributeWithAnyValue("kafka.network.processor.id")));

    if (useZookeeper) {
      // those metrics are only reported when using zookeeper
      verifier
          .add(
              "kafka.leaderElection.count",
              metric ->
                  metric
                      .isCounter()
                      .hasUnit("{elections}")
                      .hasDescription("The leader election count")
                      .hasDataPointsWithoutAttributes())
          .add(
              "kafka.leaderElection.unclean.count",
              metric ->
                  metric
                      .isCounter()
                      .hasUnit("{elections}")
                      .hasDescription(
                          "Unclean leader election count - increasing indicates broker failures")
                      .hasDataPointsWithoutAttributes());
    }
    return verifier;
  }
}
