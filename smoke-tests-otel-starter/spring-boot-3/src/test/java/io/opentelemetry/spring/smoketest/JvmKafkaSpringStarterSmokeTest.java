/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DisabledInNativeImage // See GraalVmNativeKafkaSpringStarterSmokeTest for the GraalVM native test
public class JvmKafkaSpringStarterSmokeTest extends AbstractKafkaSpringStarterSmokeTest {

  @Container @ServiceConnection
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.10"))
          .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
          .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
          .withStartupTimeout(Duration.ofMinutes(1));
}
