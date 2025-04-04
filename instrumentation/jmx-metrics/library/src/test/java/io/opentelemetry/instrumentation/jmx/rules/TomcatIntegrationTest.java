/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class TomcatIntegrationTest extends TargetSystemTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
          "tomcat:10.0",
          "tomcat:9.0"
      })
  void testCollectedMetrics(String dockerImageName) throws Exception {
    List<String> yamlFiles = Collections.singletonList("tomcat.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    // testing with a basic tomcat image as test application to capture JVM metrics
    GenericContainer<?> target =
        new GenericContainer<>(dockerImageName)
            .withEnv("CATALINA_OPTS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPorts(8080));

    copyFilesToTarget(target, yamlFiles);

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    return MetricsVerifier.create()
        .add(
            "tomcat.error.count",
            metric ->
                metric
                    .hasDescription("The number of errors.")
                    .hasUnit("{error}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attribute("tomcat.request_processor.name", "\"http-nio-8080\"")))
        .add(
            "tomcat.request.count",
            metric ->
                metric
                    .hasDescription("The number of requests processed.")
                    .hasUnit("{request}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attribute("tomcat.request_processor.name", "\"http-nio-8080\"")))
        .add(
            "tomcat.request.duration.max",
            metric ->
                metric
                    .hasDescription("The longest request processing time.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithOneAttribute(attribute("tomcat.request_processor.name", "\"http-nio-8080\"")))
        .add(
            "tomcat.request.processing_time",
            metric ->
                metric
                    .hasDescription("Total time for processing all requests.")
                    .hasUnit("s")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(attribute("tomcat.request_processor.name", "\"http-nio-8080\"")))
        .add(
            "tomcat.network.io",
            metric ->
                metric
                    .hasDescription("The number of bytes transmitted and received")
                    .hasUnit("By")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attribute("tomcat.network.io.direction", "sent"),
                            attribute("tomcat.request_processor.name", "\"http-nio-8080\"")),
                        attributeGroup(
                            attribute("tomcat.network.io.direction", "received"),
                            attribute("tomcat.request_processor.name", "\"http-nio-8080\""))))

        .add(
            "tomcat.active_session.count",
            metric ->
                metric
                    .hasDescription("The number of active sessions.")
                    .hasUnit("{session}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("tomcat.web_app_context")))

        .add(
            "tomcat.thread.count",
            metric ->
                metric
                    .hasDescription("Thread count of the thread pool.")
                    .hasUnit("{thread}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attribute("state", "idle"),
                            attribute("tomcat.thread_pool.name", "\"http-nio-8080\"")),
                        attributeGroup(
                            attribute("state", "busy"),
                            attribute("tomcat.thread_pool.name", "\"http-nio-8080\""))));

  }
}
