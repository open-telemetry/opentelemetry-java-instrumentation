/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka.KafkaInstrumentationSpringBoot4AutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread.ThreadDetailsAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@DisabledInNativeImage // See GraalVmNativeKafkaSpringStarterSmokeTest for the GraalVM native test
class KafkaSpringStarterSmokeTest extends AbstractKafkaSpringStarterSmokeTest {
  static KafkaContainer kafka;

  private ApplicationContextRunner contextRunner;

  @BeforeAll
  static void setUpKafka() {
    kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.Kafka.*Server\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();
  }

  @AfterAll
  static void tearDownKafka() {
    kafka.stop();
  }

  @BeforeEach
  void setUpContext() {
    contextRunner =
        new ApplicationContextRunner()
            .withAllowBeanDefinitionOverriding(true)
            .withConfiguration(
                AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    ThreadDetailsAutoConfiguration.class,
                    SpringSmokeOtelConfiguration.class,
                    KafkaAutoConfiguration.class,
                    KafkaInstrumentationSpringBoot4AutoConfiguration.class,
                    KafkaConfig.class))
            .withPropertyValues(
                "otel.instrumentation.kafka.experimental-span-attributes=true",
                "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.linger-ms=10",
                "spring.kafka.listener.idle-between-polls=1000",
                "spring.kafka.producer.transaction-id-prefix=test-");
  }

  @SuppressWarnings("unchecked") // we lose parameter types for the KafkaTemplate
  @Override
  @Test
  void shouldInstrumentProducerAndConsumer() {
    contextRunner.run(
        applicationContext -> {
          testing = new SpringSmokeTestRunner(applicationContext.getBean(OpenTelemetry.class));
          kafkaTemplate = applicationContext.getBean(KafkaTemplate.class);
          super.shouldInstrumentProducerAndConsumer();
        });
  }
}
