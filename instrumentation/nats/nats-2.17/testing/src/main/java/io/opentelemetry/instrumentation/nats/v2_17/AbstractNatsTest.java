/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractNatsTest {

  static DockerImageName natsImage;
  static GenericContainer<?> natsContainer;
  static Connection connection;

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  static void beforeAll() throws IOException, InterruptedException {
    natsImage = DockerImageName.parse("nats:2.11.2-alpine3.21");

    natsContainer = new GenericContainer<>(natsImage).withExposedPorts(4222);
    natsContainer.start();

    String host = natsContainer.getHost();
    Integer port = natsContainer.getMappedPort(4222);
    connection = Nats.connect("nats://" + host + ":" + port);
  }

  @AfterAll
  static void afterAll() throws InterruptedException, TimeoutException {
    connection.drain(Duration.ZERO);
    natsContainer.close();
  }
}
