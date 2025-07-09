package io.opentelemetry.instrumentation.jmx.rules;

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

    GenericContainer<?> target = new GenericContainer<>(dockerImage)
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
    return MetricsVerifier.create();
  }
}
