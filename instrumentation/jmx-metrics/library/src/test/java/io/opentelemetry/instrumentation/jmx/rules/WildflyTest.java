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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class WildflyTest extends TargetSystemTest {

  private static final int WILDFLY_SERVICE_PORT = 8080;

  @ParameterizedTest
  @ValueSource(strings = {"quay.io/wildfly/wildfly:32.0.1.Final-jdk11"})
  public void testWildflyMetrics(String dockerImage) {
    List<String> yamlFiles = Collections.singletonList("wildfly.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>(dockerImage)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(WILDFLY_SERVICE_PORT)
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .waitingFor(Wait.forListeningPorts(WILDFLY_SERVICE_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);
    copyTestWebAppToTarget(target, "/opt/jboss/wildfly/standalone/deployments/testapp.war");

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher deploymentAttribute = attribute("wildfly.deployment", "testapp.war");
    AttributeMatcher serverAttribute = attribute("wildfly.server", "default-server");
    AttributeMatcher listenerAttribute = attribute("wildfly.listener", "default");
    AttributeMatcherGroup serverListenerAttributes =
        attributeGroup(serverAttribute, listenerAttribute);

    return MetricsVerifier.create()
        // session metrics
        .add(
            "wildfly.session.created",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of sessions created")
                    .hasUnit("{session}")
                    .hasDataPointsWithOneAttribute(deploymentAttribute))
        .add(
            "wildfly.session.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of active sessions")
                    .hasUnit("{session}")
                    .hasDataPointsWithOneAttribute(deploymentAttribute))
        .add(
            "wildfly.session.expired",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of expired sessions")
                    .hasUnit("{session}")
                    .hasDataPointsWithOneAttribute(deploymentAttribute))
        .add(
            "wildfly.session.rejected",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of rejected sessions")
                    .hasUnit("{session}")
                    .hasDataPointsWithOneAttribute(deploymentAttribute))
        // request metrics
        .add(
            "wildfly.request.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of requests received")
                    .hasUnit("{request}")
                    .hasDataPointsWithAttributes(serverListenerAttributes))
        .add(
            "wildfly.request.duration.sum",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total amount of time spent processing requests")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(serverListenerAttributes))
        .add(
            "wildfly.error.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of requests that have resulted in a 5xx response")
                    .hasUnit("{request}")
                    .hasDataPointsWithAttributes(serverListenerAttributes))
        // network io metrics
        .add(
            "wildfly.network.io",
            metric ->
                metric
                    .hasDescription("Total number of bytes transferred")
                    .hasUnit("By")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attribute("network.io.direction", "receive"),
                            serverAttribute,
                            listenerAttribute),
                        attributeGroup(
                            attribute("network.io.direction", "transmit"),
                            serverAttribute,
                            listenerAttribute)));
  }
}
