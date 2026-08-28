/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class TomcatTest extends TargetSystemTest {

  @ParameterizedTest
  @ValueSource(strings = {"tomcat:10.0", "tomcat:9.0"})
  void testCollectedMetrics(String dockerImageName) {
    List<String> yamlFiles = singletonList("tomcat.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>(dockerImageName)
            .withEnv("CATALINA_OPTS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/datasource.jsp").forPort(8080).forStatusCode(200));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    // Deploy example web application to the tomcat to enable reporting tomcat.session.active.count
    // metric
    copyTestWebAppToTarget(target, "/usr/local/tomcat/webapps/ROOT.war");
    target.withCopyFileToContainer(
        MountableFile.forClasspathResource("tomcat-context.xml"),
        "/usr/local/tomcat/conf/context.xml");
    target.withCopyFileToContainer(
        MountableFile.forClasspathResource("datasource.jsp"),
        "/usr/local/tomcat/webapps/ROOT/datasource.jsp");

    startWeaverValidation(
        "tomcat.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("tomcat.")
                .checkRegisteredMetrics(
                    "tomcat.",
                    asList(
                        "tomcat.network.io",
                        "tomcat.thread.busy.count",
                        "tomcat.request.duration.sum",
                        "tomcat.request.count",
                        "tomcat.thread.count",
                        "tomcat.session.active.count",
                        "tomcat.session.active.limit",
                        "tomcat.error.count",
                        "tomcat.request.duration.max",
                        "tomcat.thread.limit",
                        "tomcat.session.duration.max",
                        "tomcat.session.created",
                        "tomcat.session.duration.mean",
                        "tomcat.session.processing.duration.sum",
                        "tomcat.session.active.max",
                        "tomcat.session.expired",
                        "tomcat.session.rejected",
                        "tomcat.db.client.connection.initial",
                        "tomcat.db.client.connection.count",
                        "tomcat.db.client.connection.limit"),
                    emptyList())
                .checkRegisteredAttributes(
                    "tomcat.",
                    asList(
                        "tomcat.request.processor.name",
                        "tomcat.context",
                        "tomcat.thread.pool.name"),
                    emptyList())
                .checkRegisteredAttributes(
                    "db.client.",
                    asList("db.client.connection.pool.name", "db.client.connection.state"),
                    emptyList()));

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher requestProcessorNameAttribute =
        attribute("tomcat.request.processor.name", "\"http-nio-8080\"");
    AttributeMatcher threadPoolNameAttribute =
        attribute("tomcat.thread.pool.name", "\"http-nio-8080\"");
    AttributeMatcher dataSourcePoolNameAttribute =
        attribute("db.client.connection.pool.name", "\"jdbc/TestDB\"");
    AttributeMatcher usedConnectionStateAttribute = attribute("db.client.connection.state", "used");
    AttributeMatcher idleConnectionStateAttribute = attribute("db.client.connection.state", "idle");

    return MetricsVerifier.create()
        .add(
            "tomcat.db.client.connection.initial",
            metric ->
                metric
                    .hasDescription("The configured initial size of the JDBC connection pool.")
                    .hasUnit("{connection}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(dataSourcePoolNameAttribute))
        .add(
            "tomcat.db.client.connection.count",
            metric ->
                metric
                    .hasDescription("The number of JDBC connections.")
                    .hasUnit("{connection}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(dataSourcePoolNameAttribute, usedConnectionStateAttribute),
                        attributeGroup(dataSourcePoolNameAttribute, idleConnectionStateAttribute)))
        .add(
            "tomcat.db.client.connection.limit",
            metric ->
                metric
                    .hasDescription("The configured maximum size of the JDBC connection pool.")
                    .hasUnit("{connection}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(dataSourcePoolNameAttribute))
        .add(
            "tomcat.error.count",
            metric ->
                metric
                    .hasDescription("The number of errors.")
                    .hasUnit("{error}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(requestProcessorNameAttribute))
        .add(
            "tomcat.request.count",
            metric ->
                metric
                    .hasDescription("The number of requests processed.")
                    .hasUnit("{request}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(requestProcessorNameAttribute))
        .add(
            "tomcat.request.duration.max",
            metric ->
                metric
                    .hasDescription("The longest request processing time.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(requestProcessorNameAttribute))
        .add(
            "tomcat.request.duration.sum",
            metric ->
                metric
                    .hasDescription("Total time of processing all requests.")
                    .hasUnit("s")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(requestProcessorNameAttribute))
        .add(
            "tomcat.network.io",
            metric ->
                metric
                    .hasDescription("The number of bytes transmitted.")
                    .hasUnit("By")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attribute("network.io.direction", "receive"),
                            requestProcessorNameAttribute),
                        attributeGroup(
                            attribute("network.io.direction", "transmit"),
                            requestProcessorNameAttribute)))
        .add(
            "tomcat.session.active.count",
            metric ->
                metric
                    .hasDescription("The number of currently active sessions.")
                    .hasUnit("{session}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.active.limit",
            metric ->
                metric
                    .hasDescription("Maximum possible number of active sessions.")
                    .hasUnit("{session}")
                    .isUpDownCounter()
                    .hasDataPointsWithIntValues(value -> value.isGreaterThanOrEqualTo(0))
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.duration.max",
            metric ->
                metric
                    .hasDescription("The maximum observed session lifetime.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.created",
            metric ->
                metric
                    .hasDescription("The number of sessions created.")
                    .hasUnit("{session}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.duration.mean",
            metric ->
                metric
                    .hasDescription("The average observed session lifetime.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.processing.duration.sum",
            metric ->
                metric
                    .hasDescription("The total time spent processing sessions.")
                    .hasUnit("s")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.active.max",
            metric ->
                metric
                    .hasDescription("The maximum number of concurrent active sessions observed.")
                    .hasUnit("{session}")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.expired",
            metric ->
                metric
                    .hasDescription("The number of expired sessions.")
                    .hasUnit("{session}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.session.rejected",
            metric ->
                metric
                    .hasDescription("The number of rejected sessions.")
                    .hasUnit("{session}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.context")))
        .add(
            "tomcat.thread.count",
            metric ->
                metric
                    .hasDescription("Total thread count of the thread pool.")
                    .hasUnit("{thread}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(threadPoolNameAttribute))
        .add(
            "tomcat.thread.limit",
            metric ->
                metric
                    .hasDescription("Maximum possible number of threads in the thread pool.")
                    .hasUnit("{thread}")
                    .isUpDownCounter()
                    .hasDataPointsWithIntValues(value -> value.isGreaterThanOrEqualTo(0))
                    .hasDataPointsWithOneAttribute(threadPoolNameAttribute))
        .add(
            "tomcat.thread.busy.count",
            metric ->
                metric
                    .hasDescription("Number of busy threads in the thread pool.")
                    .hasUnit("{thread}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(threadPoolNameAttribute));
  }
}
