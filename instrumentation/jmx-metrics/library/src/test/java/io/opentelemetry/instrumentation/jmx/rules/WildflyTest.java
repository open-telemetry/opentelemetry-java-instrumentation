/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class WildflyTest extends TargetSystemTest {

  private static final int WILDFLY_SERVICE_PORT = 8080;

  @ParameterizedTest
  @ValueSource(
      strings = {
        // keep testing on old and deprecated version for compatibility
        "jboss/wildfly:10.1.0.Final",
        // recent/latest to be maintained as newer versions are released
        "quay.io/wildfly/wildfly:36.0.1.Final-jdk21"
      })
  void testWildflyMetrics(String dockerImage) throws IOException {
    List<String> yamlFiles = singletonList("wildfly.yaml");

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
    exerciseTestApp(target, dockerImage);
    resetMetrics();

    verifyMetrics(createMetricsVerifier());
  }

  private static void exerciseTestApp(GenericContainer<?> target, String dockerImage)
      throws IOException {
    URL url =
        URI.create(
                "http://"
                    + target.getHost()
                    + ":"
                    + target.getMappedPort(WILDFLY_SERVICE_PORT)
                    + testAppPath(dockerImage))
            .toURL();

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreException(IOException.class)
        .untilAsserted(() -> assertThat(getStatusCode(url)).isEqualTo(200));
  }

  private static int getStatusCode(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(1_000);
    connection.setReadTimeout(5_000);
    try {
      return connection.getResponseCode();
    } finally {
      connection.disconnect();
    }
  }

  private static String testAppPath(String dockerImage) {
    return dockerImage.startsWith("jboss/wildfly:") ? "/testapp/javax/" : "/testapp/jakarta/";
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher deploymentAttribute = attribute("wildfly.deployment", "testapp.war");
    AttributeMatcher serverAttribute = attribute("wildfly.server", "default-server");
    AttributeMatcher listenerAttribute = attribute("wildfly.listener", "default");
    AttributeMatcherGroup serverListenerAttributes =
        attributeGroup(serverAttribute, listenerAttribute);

    AttributeMatcher dataSourceAttribute = attribute("db.client.connection.pool.name", "ExampleDS");

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
            "wildfly.session.active.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of active sessions")
                    .hasUnit("{session}")
                    .hasDataPointsWithOneAttribute(deploymentAttribute))
        .add(
            "wildfly.session.active.limit",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The maximum number of active sessions")
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
                    .hasDescription("The number of requests served")
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
                            listenerAttribute)))
        // database connection pool metrics
        .add(
            "wildfly.db.client.connection.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of open physical database connections")
                    .hasUnit("{connection}")
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            dataSourceAttribute, attribute("db.client.connection.state", "used")),
                        attributeGroup(
                            dataSourceAttribute, attribute("db.client.connection.state", "idle"))))
        .add(
            "wildfly.db.client.connection.wait.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The number of connection requests that had to wait to obtain it")
                    .hasUnit("{request}")
                    .hasDataPointsWithOneAttribute(dataSourceAttribute))
        // transactions
        .add(
            "wildfly.transaction.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of in-flight transactions")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "wildfly.transaction.created",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions created")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "wildfly.transaction.rollback",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions rolled back")
                    .hasUnit("{transaction}")
                    // older versions do not report 'system' cause, hence non-strict assertion
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("wildfly.rollback.cause")))
        .add(
            "wildfly.transaction.committed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions committed")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes());
  }
}
