/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.kafka;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.MetricsVerifier;
import io.opentelemetry.instrumentation.jmx.rules.TargetSystemTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class KafkaBrokerTest extends TargetSystemTest {

  private static final String KAFKA_IMAGE = "apache/kafka:3.8.0";

  @Test
  void kafkaBroker() {
    List<String> yamlFiles = singletonList("experimental-kafka-broker.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target = KafkaContainerFactory.createKafkaContainer(KAFKA_IMAGE)
        .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    // TODO: add weaver validation

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    return MetricsVerifier.create();
  }
}
