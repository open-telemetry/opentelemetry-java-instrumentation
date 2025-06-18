/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class JettyIntegrationTest extends TargetSystemTest {

  private static final int JETTY_PORT = 8080;

  @ParameterizedTest(name = "jetty:{arguments}")
  @ValueSource(ints = {9, 10, 11, 12})
  void testCollectedMetrics(int jettyMajorVersion) {

    List<String> yamlFiles = Collections.singletonList("jetty.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    String jettyModules = "jmx,http,";
    if (jettyMajorVersion >= 12) {
      jettyModules += "statistics";
    } else {
      jettyModules += "stats";
    }
    String addModulesArg = "--add-to-startd=" + jettyModules;

    GenericContainer<?> container =
        new GenericContainer<>(
                new ImageFromDockerfile()
                    .withDockerfileFromBuilder(
                        builder ->
                            builder
                                .from("jetty:" + jettyMajorVersion)
                                .run("java", "-jar", "/usr/local/jetty/start.jar", addModulesArg)
                                .run("mkdir -p /var/lib/jetty/webapps/ROOT/")
                                .run("touch /var/lib/jetty/webapps/ROOT/index.html")
                                .build()))
            .withEnv("JAVA_OPTIONS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(JETTY_PORT)
            .waitingFor(Wait.forListeningPorts(JETTY_PORT));

    copyFilesToTarget(container, yamlFiles);

    startTarget(container);

    verifyMetrics(createMetricsVerifier(jettyMajorVersion));
  }

  private static MetricsVerifier createMetricsVerifier(int jettyMajorVersion) {

    AttributeMatcherGroup threadPoolAttributes;
    if (jettyMajorVersion >= 12) {
      threadPoolAttributes =
          attributeGroup(
              attributeWithAnyValue("jetty.thread.pool.id"),
              attributeWithAnyValue("jetty.thread.context"));
    } else {
      threadPoolAttributes = attributeGroup(attributeWithAnyValue("jetty.thread.pool.id"));
    }

    MetricsVerifier verifier =
        MetricsVerifier.create()
            .add(
                "jetty.thread.count",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasDescription("The current number of threads")
                        .hasUnit("{thread}")
                        .hasDataPointsWithAttributes(threadPoolAttributes))
            .add(
                "jetty.thread.limit",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasDescription("The configured maximum number of threads in the pool")
                        .hasUnit("{thread}")
                        .hasDataPointsWithAttributes(threadPoolAttributes))
            .add(
                "jetty.thread.idle.count",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasDescription("The current number of idle threads")
                        .hasUnit("{thread}")
                        .hasDataPointsWithAttributes(threadPoolAttributes))
            .add(
                "jetty.thread.busy.count",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasDescription("The current number of busy threads")
                        .hasUnit("{thread}")
                        .hasDataPointsWithAttributes(threadPoolAttributes))
            .add(
                "jetty.thread.queue.size",
                metric ->
                    metric
                        .isUpDownCounter()
                        .hasDescription("The current job queue size")
                        .hasUnit("{thread}")
                        .hasDataPointsWithAttributes(threadPoolAttributes))
            .add(
                "jetty.select.count",
                metric ->
                    metric
                        .isCounter()
                        .hasDescription("The number of select calls")
                        .hasUnit("{operation}")
                        .hasDataPointsWithAttributes(
                            attributeGroup(
                                attributeWithAnyValue("jetty.selector.id"),
                                attributeWithAnyValue("jetty.selector.context"))));

    if (jettyMajorVersion < 12) {
      verifier
          .add(
              "jetty.session.created.count",
              metric ->
                  metric
                      .isCounter()
                      .hasDescription("The total number of created sessions")
                      .hasUnit("{session}")
                      .hasDataPointsWithAttributes(
                          attributeGroup(
                              attributeWithAnyValue("jetty.context"),
                              attributeWithAnyValue("jetty.session.handler.id"))))
          .add(
              "jetty.session.duration.sum",
              metric ->
                  metric
                      .isCounter()
                      .hasDescription("The cumulated session duration")
                      .hasUnit("s")
                      .hasDataPointsWithAttributes(
                          attributeGroup(
                              attributeWithAnyValue("jetty.context"),
                              attributeWithAnyValue("jetty.session.handler.id"))))
          .add(
              "jetty.session.duration.max",
              metric ->
                  metric
                      .isGauge()
                      .hasDescription("The maximum session duration")
                      .hasUnit("s")
                      .hasDataPointsWithAttributes(
                          attributeGroup(
                              attributeWithAnyValue("jetty.context"),
                              attributeWithAnyValue("jetty.session.handler.id"))))
          .add(
              "jetty.session.duration.mean",
              metric ->
                  metric
                      .isGauge()
                      .hasDescription("The mean session duration")
                      .hasUnit("s")
                      .hasDataPointsWithAttributes(
                          attributeGroup(
                              attributeWithAnyValue("jetty.context"),
                              attributeWithAnyValue("jetty.session.handler.id"))));
    }

    return verifier;
  }
}
