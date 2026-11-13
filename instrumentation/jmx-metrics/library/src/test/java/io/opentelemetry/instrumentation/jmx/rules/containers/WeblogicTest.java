/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.containers;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class WeblogicTest extends TargetSystemTest {

  private static final int WEBLOGIC_PORT = 7001;

  static Stream<Arguments> weblogicArgs() {
    return Stream.of(
        arguments("container-registry.oracle.com/middleware/weblogic:14.1.1.0-8-ol8", true),
        arguments("container-registry.oracle.com/middleware/weblogic:14.1.1.0-8-ol8", false),
        arguments("container-registry.oracle.com/middleware/weblogic:14.1.1.0-11-ol8", true),
        arguments("container-registry.oracle.com/middleware/weblogic:14.1.1.0-11-ol8", false),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:14.1.2.0-generic-jdk17-ol8", true),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:14.1.2.0-generic-jdk17-ol8", false),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:14.1.2.0-generic-jdk21-ol8", true),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:14.1.2.0-generic-jdk21-ol8", false),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:15.1.1.0-generic-jdk17-ol8", true),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:15.1.1.0-generic-jdk17-ol8", false),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:15.1.1.0-generic-jdk21-ol8", true),
        arguments(
            "container-registry.oracle.com/middleware/weblogic:15.1.1.0-generic-jdk21-ol8", false));
  }

  private static boolean isImageAvailableLocally(String dockerImageName) {
    try {
      DockerClientFactory.instance().client().inspectImageCmd(dockerImageName).exec();
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  @ParameterizedTest
  @MethodSource("weblogicArgs")
  void testCollectedMetrics(String dockerImageName, boolean experimentalOptIn) {
    // Oracle WebLogic images require authenticated Oracle Container Registry login and license
    // acceptance. Run when the image is available locally or when explicitly opted in via
    // -Dio.opentelemetry.jmx.weblogic.enabled=true.
    assumeTrue(
        Boolean.getBoolean("io.opentelemetry.jmx.weblogic.enabled")
            || isImageAvailableLocally(dockerImageName),
        "Skipping WebLogic container test because "
            + dockerImageName
            + " is not pulled locally (pull it or pass -Dio.opentelemetry.jmx.weblogic.enabled=true)");

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());

    List<String> yamlFiles = new ArrayList<>();
    Map<String, String> config;
    if (experimentalOptIn) {
      yamlFiles = emptyList();
      config = otelConfigPropertiesExperimentalOptIn("weblogic.*");
    } else {
      yamlFiles.addAll(getAllRulesForSystem("weblogic"));
      config = otelConfigProperties(yamlFiles);
    }

    jvmArgs.addAll(javaPropertiesToJvmArgs(config));

    GenericContainer<?> target =
        new GenericContainer<>(dockerImageName)
            .withEnv("PRODUCTION_MODE", "dev")
            .withEnv("ADMINISTRATION_PORT_ENABLED", "false")
            .withEnv("WLS_AGENT_OPTS", String.join(" ", jvmArgs))
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("weblogic/domain.properties"),
                "/u01/oracle/properties/domain.properties")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("weblogic/create-wls-domain.py"),
                "/u01/oracle/create-wls-domain.py")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("weblogic/start-wls-test.sh", 0755),
                "/u01/oracle/start-wls-test.sh")
            .withCommand("/u01/oracle/start-wls-test.sh")
            .withStartupTimeout(Duration.ofMinutes(4))
            .withExposedPorts(WEBLOGIC_PORT)
            .waitingFor(
                Wait.forLogMessage(".*The server started in RUNNING mode.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(4)));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);
    copyTestWebAppToTarget(target, "/u01/oracle/testapp.war");

    startWeblogicWeaverValidation();

    startTarget(target);

    sendHttpRequestToDeployedApp(target, dockerImageName);

    verifyMetrics(createMetricsVerifier());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCollectedMetricsWithSyntheticMBeans(boolean experimentalOptIn) {
    List<String> yamlFiles = new ArrayList<>();
    Map<String, String> config;
    if (experimentalOptIn) {
      config = otelConfigPropertiesExperimentalOptIn("weblogic.*");
    } else {
      yamlFiles.addAll(getAllRulesForSystem("weblogic"));
      config = otelConfigProperties(yamlFiles);
    }

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(config));

    GenericContainer<?> target =
        new GenericContainer<>("eclipse-temurin:17.0.18_8-jre")
            .withCommand(
                String.format("java %s -jar /weblogic_test.jar", String.join(" ", jvmArgs)))
            .waitingFor(
                Wait.forLogMessage(".*Synthetic WebLogic runtime MBeans registered.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(1)));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);
    copyTestAppToTarget(
        getArtifactPath("io.opentelemetry.weblogictestapp.path"), target, "/weblogic_test.jar");

    startWeblogicWeaverValidation();
    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private void startWeblogicWeaverValidation() {
    startWeaverValidation(
        "weblogic.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("weblogic.")
                .checkRegisteredMetrics(
                    "weblogic.",
                    asList(
                        "weblogic.thread.count",
                        "weblogic.thread.idle.count",
                        "weblogic.thread.standby.count",
                        "weblogic.thread.stuck.count",
                        "weblogic.thread.queue.size",
                        "weblogic.session.active.count",
                        "weblogic.session.created",
                        "weblogic.request.count",
                        "weblogic.request.duration.sum",
                        "weblogic.request.duration.max",
                        "weblogic.network.io",
                        "weblogic.db.client.connection.count",
                        "weblogic.db.client.connection.wait.count",
                        "weblogic.transaction.count",
                        "weblogic.transaction.completed",
                        "weblogic.transaction.committed",
                        "weblogic.transaction.rollback",
                        "weblogic.transaction.abandoned"),
                    emptyList())
                .checkRegisteredAttributes(
                    "weblogic.",
                    asList(
                        "weblogic.application",
                        "weblogic.context",
                        "weblogic.servlet",
                        "weblogic.channel",
                        "weblogic.rollback.cause"),
                    emptyList()));
  }

  private static void sendHttpRequestToDeployedApp(
      GenericContainer<?> target, String dockerImageName) {
    String baseUri = "http://" + target.getHost() + ":" + target.getMappedPort(WEBLOGIC_PORT);
    WebClient client = WebClient.of(baseUri);
    // WebLogic 14.x uses javax.servlet; WebLogic 15.x+ uses jakarta.servlet
    String path = dockerImageName.contains(":15.") ? "/testapp/jakarta" : "/testapp/javax";
    AggregatedHttpResponse response = client.get(path).aggregate().join();
    assertThat(response.status()).isEqualTo(HttpStatus.OK);
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher applicationAttribute = attributeWithAnyValue("weblogic.application");
    AttributeMatcher contextAttribute = attributeWithAnyValue("weblogic.context");
    AttributeMatcher servletAttribute = attributeWithAnyValue("weblogic.servlet");
    AttributeMatcher channelAttribute = attributeWithAnyValue("weblogic.channel");
    AttributeMatcherGroup servletAttributes =
        attributeGroup(applicationAttribute, contextAttribute, servletAttribute);
    AttributeMatcher dataSourceAttribute = attribute("db.client.connection.pool.name", "ExampleDS");

    return MetricsVerifier.create()
        // thread pool metrics
        .add(
            "weblogic.thread.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The total number of threads in the pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0))
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.thread.idle.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of idle threads in the pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.thread.standby.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of threads in the standby pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0))
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.thread.stuck.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("Number of stuck threads in the thread pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.thread.queue.size",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of pending requests in the priority queue.")
                    .hasUnit("{request}")
                    .hasDataPointsWithoutAttributes())
        // session metrics
        .add(
            "weblogic.session.active.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of currently active sessions.")
                    .hasUnit("{session}")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0), v -> v.isEqualTo(0))
                    .hasDataPointsWithOneAttribute(contextAttribute))
        .add(
            "weblogic.session.created",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of sessions opened.")
                    .hasUnit("{session}")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0), v -> v.isEqualTo(0))
                    .hasDataPointsWithOneAttribute(contextAttribute))
        // servlet request metrics
        .add(
            "weblogic.request.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The number of times the servlet has been invoked.")
                    .hasUnit("{request}")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0), v -> v.isEqualTo(0))
                    .hasDataPointsWithAttributes(servletAttributes))
        .add(
            "weblogic.request.duration.sum",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total amount of time spent executing servlet requests.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(servletAttributes))
        .add(
            "weblogic.request.duration.max",
            metric ->
                metric
                    .isGauge()
                    .hasDescription("The longest servlet request execution time.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(servletAttributes))
        // network channel I/O metrics
        .add(
            "weblogic.network.io",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("Total number of bytes transferred on the channel.")
                    .hasUnit("By")
                    .hasDataPointsWithIntValues(v -> v.isGreaterThan(0), v -> v.isEqualTo(0))
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attribute("network.io.direction", "receive"), channelAttribute),
                        attributeGroup(
                            attribute("network.io.direction", "transmit"), channelAttribute)))
        // JDBC connection pool metrics (pool has InitialCapacity=1, MaxCapacity=5; idle is 1 and
        // used is 0)
        .add(
            "weblogic.db.client.connection.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of open physical database connections.")
                    .hasUnit("{connection}")
                    .hasDataPointsWithIntValues(v -> v.isEqualTo(1), v -> v.isEqualTo(0))
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            dataSourceAttribute, attribute("db.client.connection.state", "used")),
                        attributeGroup(
                            dataSourceAttribute, attribute("db.client.connection.state", "idle"))))
        .add(
            "weblogic.db.client.connection.wait.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The number of connection requests that had to wait to obtain a connection.")
                    .hasUnit("{request}")
                    .hasDataPointsWithOneAttribute(dataSourceAttribute))
        // JTA transaction metrics
        .add(
            "weblogic.transaction.count",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of active transactions.")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.transaction.completed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions processed.")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.transaction.committed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions committed.")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes())
        .add(
            "weblogic.transaction.rollback",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions rolled back.")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("weblogic.rollback.cause", "application")),
                        attributeGroup(attribute("weblogic.rollback.cause", "resource")),
                        attributeGroup(attribute("weblogic.rollback.cause", "system")),
                        attributeGroup(attribute("weblogic.rollback.cause", "timeout"))))
        .add(
            "weblogic.transaction.abandoned",
            metric ->
                metric
                    .isCounter()
                    .hasDescription("The total number of transactions abandoned.")
                    .hasUnit("{transaction}")
                    .hasDataPointsWithoutAttributes());
  }
}
