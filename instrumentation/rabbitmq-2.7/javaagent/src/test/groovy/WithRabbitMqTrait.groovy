/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.rabbitmq.client.ConnectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait

import java.time.Duration

trait WithRabbitMqTrait {
  private static final Logger logger = LoggerFactory.getLogger("io.opentelemetry.testing.rabbitmq-container")

  static GenericContainer rabbitMqContainer
  static ConnectionFactory connectionFactory

  def startRabbit() {
    rabbitMqContainer = new GenericContainer('rabbitmq:latest')
      .withExposedPorts(5672)
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
      .withStartupTimeout(Duration.ofMinutes(2))
    rabbitMqContainer.start()

    connectionFactory = new ConnectionFactory(
      host: rabbitMqContainer.containerIpAddress,
      port: rabbitMqContainer.getMappedPort(5672)
    )
  }

  def stopRabbit() {
    rabbitMqContainer?.stop()
  }
}