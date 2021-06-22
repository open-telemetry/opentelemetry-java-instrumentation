/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.rabbitmq.client.ConnectionFactory
import java.time.Duration
import org.testcontainers.containers.GenericContainer

trait WithRabbitMqTrait {

  static GenericContainer rabbitMqContainer
  static ConnectionFactory connectionFactory

  def startRabbit() {
    rabbitMqContainer = new GenericContainer('rabbitmq:latest')
      .withExposedPorts(5672)
      .withStartupTimeout(Duration.ofSeconds(120))
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