/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class ActiveMqTest extends TargetSystemTest {

  private static final int ACTIVEMQ_PORT = 61616;

  // TODO: test both 'classic' and 'artemis' variants

  @Test
  void activemqTest() {
    List<String> yamlFiles = Collections.singletonList("activemq.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>(
                new ImageFromDockerfile()
                    .withDockerfileFromBuilder(
                        builder -> builder.from("apache/activemq-classic:6.1.7").build()))
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(ACTIVEMQ_PORT)
            .waitingFor(Wait.forListeningPorts(ACTIVEMQ_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {

    AttributeMatcher messagingSystem = attribute("messaging.system", "activemq");
    // known attributes from the single topic available by default
    AttributeMatcher destinationName =
        attribute("messaging.destination.name", "ActiveMQ.Advisory.MasterBroker");
    AttributeMatcher topic = attribute("activemq.destination.type", "topic");
    AttributeMatcher broker = attribute("activemq.broker.name", "localhost");

    AttributeMatcherGroup topicAttributes =
        attributeGroup(messagingSystem, destinationName, topic, broker);

    AttributeMatcherGroup brokerAttributes = attributeGroup(messagingSystem, broker);

    return MetricsVerifier.create()
        // consumers and producers
        .add(
            "activemq.producer.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{producer}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The number of producers attached to this destination"))
        .add(
            "activemq.consumer.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{consumer}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The number of consumers subscribed to this destination"))
        // message consumption and in-flight
        .add(
            "activemq.message.queue.size",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{message}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The current number of messages waiting to be consumed"))
        .add(
            "activemq.message.expired",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{message}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The number of messages not delivered because they expired"))
        .add(
            "activemq.message.enqueued",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{message}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The number of messages sent to this destination"))
        .add(
            "activemq.message.dequeued",
            metric ->
                metric
                    .isCounter()
                    .hasUnit("{message}")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription(
                        "The number of messages acknowledged and removed from this destination"))
        // destination memory/temp usage and limits
        .add(
            "activemq.memory.destination.usage",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The amount of used memory"))
        .add(
            "activemq.memory.destination.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The amount of configured memory limit"))
        .add(
            "activemq.temp.destination.utilization",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("1")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The percentage of non-persistent storage used"))
        .add(
            "activemq.temp.destination.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(topicAttributes)
                    .hasDescription("The amount of configured non-persistent storage limit"))
        // broker metrics
        .add(
            "activemq.connection.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("{connection}")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The number of active connections"))
        .add(
            "activemq.memory.utilization",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("1")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The percentage of broker memory used"))
        .add(
            "activemq.memory.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The amount of configured broker memory limit"))
        .add(
            "activemq.store.utilization",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("1")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The percentage of broker persistent storage used"))
        .add(
            "activemq.store.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The amount of configured broker persistent storage limit"))
        .add(
            "activemq.temp.utilization",
            metric ->
                metric
                    .isGauge()
                    .hasUnit("1")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription("The percentage of broker non-persistent storage used"))
        .add(
            "activemq.temp.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasUnit("By")
                    .hasDataPointsWithAttributes(brokerAttributes)
                    .hasDescription(
                        "The amount of configured broker non-persistent storage limit"));
  }
}
