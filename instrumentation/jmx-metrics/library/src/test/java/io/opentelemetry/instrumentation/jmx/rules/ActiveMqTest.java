package io.opentelemetry.instrumentation.jmx.rules;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActiveMqTest extends TargetSystemTest{


  private static final int ACTIVEMQ_PORT = 61616;

  // TODO: test both 'classic' and 'artemis' variants

  @Test
  void activemqTest(){
    List<String> yamlFiles = Collections.singletonList("activemq.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target = new GenericContainer<>(
        new ImageFromDockerfile()
            .withDockerfileFromBuilder(
                builder -> builder.from("apache/activemq-classic:5.18.6").build()))
        .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
        .withStartupTimeout(Duration.ofMinutes(2))
        .withExposedPorts(ACTIVEMQ_PORT)
        .waitingFor(Wait.forListeningPorts(ACTIVEMQ_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);
  }
}
