/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class TomcatIntegrationTest extends TargetSystemTest {

  @ParameterizedTest
  @CsvSource({
    "tomcat:10.0, https://tomcat.apache.org/tomcat-10.0-doc/appdev/sample/sample.war",
    "tomcat:9.0, https://tomcat.apache.org/tomcat-9.0-doc/appdev/sample/sample.war"
  })
  void testCollectedMetrics(String dockerImageName, String sampleWebApplicationUrl)
      throws Exception {
    List<String> yamlFiles = Collections.singletonList("tomcat.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>(dockerImageName)
            .withEnv("CATALINA_OPTS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPorts(8080));

    copyFilesToTarget(target, yamlFiles);

    startTarget(target);

    // Deploy example web application to the tomcat to enable reporting tomcat.session.active.count
    // metric
    target.execInContainer("rm", "-fr", "/usr/local/tomcat/webapps/ROOT");
    target.execInContainer(
        "curl", sampleWebApplicationUrl, "-o", "/usr/local/tomcat/webapps/ROOT.war");

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher requestProcessorNameAttribute =
        attribute("tomcat.request.processor.name", "\"http-nio-8080\"");
    AttributeMatcher threadPoolNameAttribute =
        attribute("tomcat.thread.pool.name", "\"http-nio-8080\"");

    return MetricsVerifier.create()
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
