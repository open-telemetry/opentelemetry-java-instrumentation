/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.rabbitmq.client.ConnectionFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

abstract class AbstractRabbitMqTest {
  protected static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes");

  static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }

  private static final Logger logger =
      LoggerFactory.getLogger("io.opentelemetry.testing.rabbitmq-container");

  private static GenericContainer<?> rabbitMqContainer;
  protected static ConnectionFactory connectionFactory;

  protected static String rabbitMqHost;

  protected static String rabbitMqIp;

  protected static int rabbitMqPort;

  @BeforeAll
  static void startRabbit() throws UnknownHostException {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:4.2")
            .withExposedPorts(5672)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    rabbitMqContainer.start();

    connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(rabbitMqContainer.getHost());
    connectionFactory.setPort(rabbitMqContainer.getMappedPort(5672));
    connectionFactory.setAutomaticRecoveryEnabled(false);

    rabbitMqHost = rabbitMqContainer.getHost();
    rabbitMqIp = InetAddress.getByName(rabbitMqContainer.getHost()).getHostAddress();
    rabbitMqPort = rabbitMqContainer.getMappedPort(5672);
  }

  @AfterAll
  static void stopRabbit() {
    if (rabbitMqContainer != null) {
      rabbitMqContainer.stop();
    }
  }
}
